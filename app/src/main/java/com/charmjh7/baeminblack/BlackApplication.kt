package com.charmjh7.baeminblack

import android.app.Application
import androidx.room.Room
import com.charmjh7.baeminblack.data.*

class BlackApplication : Application() {
    val diagnostics by lazy { com.charmjh7.baeminblack.diagnostics.DiagnosticLogger(this) }
    val serviceStatus = com.charmjh7.baeminblack.diagnostics.ServiceStatus()
    val db by lazy { Room.databaseBuilder(this, BlackDatabase::class.java, "baemin-black.db").build() }
    val repository by lazy { ShopRepository(db, MediaStore(this), diagnostics) }
    val settings by lazy { AppSettings(this) }
}
