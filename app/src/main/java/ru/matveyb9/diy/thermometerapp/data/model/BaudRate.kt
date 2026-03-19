package ru.matveyb9.diy.thermometerapp.data.model

enum class BaudRate(val value: Int) {
    BAUD_4800(4800),
    BAUD_9600(9600),
    BAUD_19200(19200),
    BAUD_38400(38400),
    BAUD_57600(57600),
    BAUD_115200(115200);

    companion object {
        /** Порядок перебора при авто-определении (самые популярные для Arduino — первые) */
        val AUTO_DETECT_ORDER = listOf(
            BAUD_9600, BAUD_115200, BAUD_57600, BAUD_38400, BAUD_19200, BAUD_4800,
        )
    }
}
