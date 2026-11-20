package wtrack.app.core.time

import wtrack.app.domain.time.Clock

class SystemClock : Clock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
