package com.snothin.ghostsam.companion

object GsdfrNative {
    init {
        System.loadLibrary("gsdfr")
    }

    external fun probe(): String

    external fun hookCheck(): String

    external fun hookArm(kmi: String, ko: ByteArray): String

    external fun hookTrigger(): String

    external fun hookFinish(force: Boolean): String

    external fun progress(op: Int): String

    external fun kmi(): String
}
