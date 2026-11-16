package org.quackulous.engine

/** The JNI surface of the engine (cpp/quackulous_jni.cpp). Use [QuackleEngine] rather than calling this directly. */
internal object NativeEngine {
    init {
        System.loadLibrary("quackulous")
    }

    fun interface BuildListener {
        /** [stage] is a [BuildStage] ordinal. Return false to cancel. */
        fun onProgress(stage: Int, fraction: Double): Boolean
    }

    fun interface SimulationListener {
        /** Return false to stop with the results so far. */
        fun onProgress(iterationsDone: Int): Boolean
    }

    @JvmStatic external fun nativeInit(dataDirectory: String): String
    @JvmStatic external fun nativeBuildLexicon(wordList: String, name: String, listener: BuildListener?): String
    @JvmStatic external fun nativeHasBuiltLexicon(name: String): Boolean
    @JvmStatic external fun nativeLoadLexicon(name: String): String?
    @JvmStatic external fun nativeIsWord(word: String): Boolean
    @JvmStatic external fun nativeSetPosition(cells: Array<String>, rack: String, myScore: Int, opponentScore: Int): String?
    @JvmStatic external fun nativeUnseenCounts(): IntArray
    @JvmStatic external fun nativeStaticMoves(count: Int): String
    @JvmStatic external fun nativeSimulate(candidates: Int, iterations: Int, plies: Int, listener: SimulationListener?): String
}
