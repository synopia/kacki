package org.synopia.kacki.plugins

import com.google.common.io.Files
import org.deeplearning4j.nn.api.OptimizationAlgorithm
import org.deeplearning4j.nn.conf.BackpropType
import org.deeplearning4j.nn.conf.NeuralNetConfiguration
import org.deeplearning4j.nn.conf.Updater
import org.deeplearning4j.nn.conf.layers.GravesLSTM
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
import org.deeplearning4j.nn.weights.WeightInit
import org.deeplearning4j.optimize.listeners.ScoreIterationListener
import org.nd4j.linalg.activations.Activation
import org.nd4j.linalg.factory.Nd4j
import org.nd4j.linalg.lossfunctions.LossFunctions
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.file.Paths
import java.util.*


fun sampleFromDistribution(distribution: DoubleArray, rng: Random): Int {
    var d = 0.0
    var sum = 0.0
    (0..10).forEach {
        d = rng.nextDouble()
        sum = 0.0
        (0 until distribution.size).forEach {
            sum += distribution[it]
            if (d <= sum) {
                return it
            }
        }
    }
    throw IllegalArgumentException("Distribution is invalid")
}

fun sampleCharactersFromNetwork(i: String?, net: MultiLayerNetwork, iter: org.synopia.kacki.plugins.CharacterIterator, rng: Random, charactersToSample: Int, numSamples: Int): List<String> {
    var initialization = i ?: iter.randomCharacter.toString()
    val initializationInput = Nd4j.zeros(numSamples, iter.inputColumns(), initialization.length)
    val init = initialization.toCharArray()
    (0 until init.size).forEach { i ->
        val idx = iter.convertCharacterToIndex(init[i])
        (0 until numSamples).forEach { j ->
            initializationInput.putScalar(listOf(j, idx, i).toIntArray(), 1.0f)
        }
    }

    val sb = Array<StringBuilder>(numSamples, { StringBuilder(initialization) })

    net.rnnClearPreviousState()
    var output = net.rnnTimeStep(initializationInput)
    output = output.tensorAlongDimension(output.size(2) - 1, 1, 0)
    (0 until charactersToSample).forEach { i ->
        val nextInput = Nd4j.zeros(numSamples, iter.inputColumns())
        (0 until numSamples).forEach { s ->
            val outputProbDistribution = DoubleArray(iter.totalOutcomes(), { output.getDouble(s, it) })
            val sampledCharacterIdx = sampleFromDistribution(outputProbDistribution, rng)
            nextInput.putScalar(listOf(s, sampledCharacterIdx).toIntArray(), 1.0f)
            sb[s].append(iter.convertIndexToCharacter(sampledCharacterIdx))
        }
        output = net.rnnTimeStep(nextInput)
    }
    return sb.map { it.toString() }
}

class CharIterator(val input: InputStream, val miniBatchSize: Int)

fun main(args: Array<String>) {
/*
    val data : Map<String, List<Event>> = Gson().fromJson(Files.readLines(Paths.get("history.json").toFile(), Charset.defaultCharset()).joinToString("\n"))
    val input = data.values.flatMap { it.filter { it.type=="m.room.message"  } }
    var lines = input.map {
        val body = Gson().fromJson<RoomMessage>(it.content)
        if (body.msgtype == "m.text" && it.sender != "@kacki:matrix.org") {
            body.body
        } else {
            null
        }
    }.filterNotNull()
*/

    val lines = Files.readLines(Paths.get("pb2229.txt").toFile(), Charset.defaultCharset())
    val lstmLayerSize = 200
    val miniBatchSize = 128
    val exampleLength = 100
    val tnpttLength = 100
    val numEpochs = 10000
    val nSamplesToGenerate = 4
    val nCharactersToSample = 300
    val generateSamplesEveryNMinibatches = 10
    val generatationInitialization: String? = null
    val rng = Random(12345)

    val iter = org.synopia.kacki.plugins.CharacterIterator(lines, miniBatchSize, exampleLength, org.synopia.kacki.plugins.CharacterIterator.getMinimalCharacterSet(), Random(12345))
    val nOut = iter.totalOutcomes()

    val conf = NeuralNetConfiguration.Builder()
            .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT).iterations(1)
            .learningRate(0.1)
            .seed(12345)
            .regularization(true)
            .l2(0.001)
            .weightInit(WeightInit.XAVIER)
            .updater(Updater.RMSPROP)
            .list()
            .layer(0, GravesLSTM.Builder().nIn(iter.inputColumns()).nOut(lstmLayerSize).activation(Activation.TANH).build())
            .layer(1, GravesLSTM.Builder().nIn(lstmLayerSize).nOut(lstmLayerSize).activation(Activation.TANH).build())
            .layer(2, RnnOutputLayer.Builder(LossFunctions.LossFunction.MCXENT).activation(Activation.SOFTMAX).nIn(lstmLayerSize).nOut(nOut).build())
            .backpropType(BackpropType.TruncatedBPTT).tBPTTForwardLength(tnpttLength).tBPTTBackwardLength(tnpttLength)
            .pretrain(false).backprop(true)
            .build()

    val net = MultiLayerNetwork(conf)
    net.init()
    net.setListeners(ScoreIterationListener(1))

    var miniBatchNumber = 0
    (0 until numEpochs).forEach {
        while (iter.hasNext()) {
            val ds = iter.next()
            net.fit(ds)
            miniBatchNumber++
            if (miniBatchNumber % generateSamplesEveryNMinibatches == 0) {
                val r = sampleCharactersFromNetwork(generatationInitialization, net, iter, rng, nCharactersToSample, nSamplesToGenerate)
                println(r.joinToString("\n"))
            }
        }
        iter.reset()
    }
}

