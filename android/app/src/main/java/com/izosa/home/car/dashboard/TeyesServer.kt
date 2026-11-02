package com.izosa.home.car.dashboard

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.RemoteException

/**
 * Car data from the Teyes "MainServer" (`com.syu.ms`), which decodes the CAN box for the stock
 * apps. Its toolkit service hands out modules over Binder (0 = main, 4 = sound, 7 = canbus);
 * each module pushes "update codes" with an int value to registered callbacks and takes
 * commands. The `com.syu.ipc` interfaces are spoken by hand here (same descriptors and
 * transaction codes as their AIDL) since only four calls are needed.
 *
 * Listeners get the raw value, or null when there is none (not connected, or stopped). Off the
 * head unit `com.syu.ms` is missing and every listener only ever sees null.
 */
class TeyesServer(private val context: Context) {

    private class Watch(val module: Int, val code: Int, val onChange: (Int?) -> Unit)

    private val handler = Handler(Looper.getMainLooper())
    private val watches = mutableListOf<Watch>()
    /** Per module: the remote module and our callback registered with it. */
    private val connected = mutableMapOf<Int, Pair<IBinder, IBinder>>()
    private var bound = false

    /** Call before [start]. */
    fun watch(module: Int, code: Int, onChange: (Int?) -> Unit) {
        watches += Watch(module, code, onChange)
    }

    private fun callback(module: Int) = object : Binder() {
        init {
            attachInterface(null, CALLBACK_DESCRIPTOR)
        }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != UPDATE) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(CALLBACK_DESCRIPTOR)
            val updateCode = data.readInt()
            val value = data.createIntArray()?.firstOrNull() ?: return true
            handler.post {
                if (bound) watches.filter { it.module == module && it.code == updateCode }.forEach { it.onChange(value) }
            }
            return true
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            for ((module, moduleWatches) in watches.groupBy { it.module }) {
                try {
                    val remote = getRemoteModule(service, module) ?: continue
                    val callback = callback(module)
                    connected[module] = remote to callback
                    // update = 1 makes the server send the current value right away.
                    for (code in moduleWatches.map { it.code }.distinct()) {
                        transact(remote, MODULE_DESCRIPTOR, REGISTER) {
                            writeStrongBinder(callback)
                            writeInt(code)
                            writeInt(1)
                        }
                    }
                } catch (e: RemoteException) {
                    connected.remove(module)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            connected.clear()
            notifyNone()
        }
    }

    fun start() {
        if (watches.isEmpty()) return
        val intent = Intent(TOOLKIT_ACTION).setPackage(SERVER_PACKAGE)
        bound = try {
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            false
        }
    }

    fun stop() {
        if (!bound) return
        for ((module, pair) in connected) {
            val (remote, callback) = pair
            for (code in watches.filter { it.module == module }.map { it.code }.distinct()) {
                try {
                    transact(remote, MODULE_DESCRIPTOR, UNREGISTER) {
                        writeStrongBinder(callback)
                        writeInt(code)
                    }
                } catch (e: RemoteException) {
                    // The server is gone; nothing to unregister from.
                }
            }
        }
        connected.clear()
        bound = false
        context.unbindService(connection)
        notifyNone()
    }

    /**
     * Sends a command to a module that has watches (so it is connected). Returns false when it
     * isn't, so callers can fall back to something else.
     */
    fun cmd(module: Int, cmdCode: Int, vararg ints: Int): Boolean {
        val remote = connected[module]?.first ?: return false
        return try {
            transact(remote, MODULE_DESCRIPTOR, CMD) {
                writeInt(cmdCode)
                writeIntArray(ints)
                writeFloatArray(null)
                writeStringArray(null)
            }
            true
        } catch (e: RemoteException) {
            false
        }
    }

    private fun notifyNone() = watches.forEach { it.onChange(null) }

    private fun getRemoteModule(toolkit: IBinder, moduleCode: Int): IBinder? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(TOOLKIT_DESCRIPTOR)
            data.writeInt(moduleCode)
            toolkit.transact(GET_REMOTE_MODULE, data, reply, 0)
            reply.readException()
            return reply.readStrongBinder()
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun transact(target: IBinder, descriptor: String, code: Int, write: Parcel.() -> Unit) {
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(descriptor)
            data.write()
            target.transact(code, data, null, IBinder.FLAG_ONEWAY)
        } finally {
            data.recycle()
        }
    }

    companion object {
        const val MODULE_MAIN = 0
        const val MODULE_SOUND = 4
        const val MODULE_CANBUS = 7

        private const val SERVER_PACKAGE = "com.syu.ms"
        private const val TOOLKIT_ACTION = "com.syu.ms.toolkit"
        private const val TOOLKIT_DESCRIPTOR = "com.syu.ipc.IRemoteToolkit"
        private const val MODULE_DESCRIPTOR = "com.syu.ipc.IRemoteModule"
        private const val CALLBACK_DESCRIPTOR = "com.syu.ipc.IModuleCallback"
        private const val GET_REMOTE_MODULE = 1
        private const val CMD = 1
        private const val REGISTER = 3
        private const val UNREGISTER = 4
        private const val UPDATE = 1
    }
}
