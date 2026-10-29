package com.okur.reader.reader

/** Okuma ekranı açıkken ses tuşlarıyla sayfa çevirmek için Activity'den gelen tuş olayları. */
object ReaderKeys {
    @Volatile
    var handler: ((keyCode: Int) -> Boolean)? = null
}
