package com.yangzhiwen.armour

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.AssetManager
import android.content.res.Resources
import com.yangzhiwen.armour.compass.ComponentType
import com.yangzhiwen.armour.compass.Navigator
import com.yangzhiwen.armour.ext.Hacker
import com.yangzhiwen.armour.ext.compass.*
import dalvik.system.DexClassLoader

/**
 * Created by yangzhiwen on 2017/8/13.
 */
class APlugin(hostContext: Context, val aPluginName: String, apkPath: String, val armour: Armour) {
    val DEX_OUT_PATH = hostContext.getDir("aplugin", Context.MODE_PRIVATE).absolutePath!!
    val aPluginClassloader = DexClassLoader(apkPath, DEX_OUT_PATH, null, hostContext.classLoader)

    val aMainActivity: String?

    val aPluginAssetManager: AssetManager
    val aPluginResources: Resources
    val aPluginContextMap = mutableMapOf<String, AContext>()
    val aPluginContext = AContext(hostContext, this, armour)

    init {
        val oldResources = hostContext.resources
        aPluginAssetManager = oldResources.assets.javaClass.newInstance()
        val result = Hacker.on(aPluginAssetManager.javaClass)
                .declaredMethod("addAssetPath", String::class.java)!!.invoke(aPluginAssetManager, apkPath) as Int
        if (result == 0) {
            throw ArmourException("addAssetPath return 0 on the plugin name: $aPluginName")
        }

        aPluginResources = Resources(aPluginAssetManager, oldResources.displayMetrics, oldResources.configuration)

        aMainActivity = APluginConfigParser.instance.parseConfig(aPluginAssetManager);

        initReceiver(hostContext)
    }

    private fun initReceiver(hostContext: Context) {
        val module = Navigator.instance.getModule(aPluginName)
        // receiver application context
        // init plugin receiver
        module?.componentMap
                ?.filter { it.value.type == ComponentType.instance.Receiver }
                ?.forEach {
                    val component = it.value as ReceiverComponent
                    val receiver = aPluginClassloader.loadClass(component.realComponent).newInstance() as BroadcastReceiver
                    val filter = IntentFilter()
                    for (action in component.actions) {
                        filter.addAction(action)
                    }
                    hostContext.registerReceiver(receiver, filter)
                }
    }

    fun start(): Boolean {
        if (aMainActivity == null) return false
        else aPluginContext.startActivity(Intent(aPluginContext, aPluginClassloader.loadClass(aMainActivity)))
        return true
    }

    fun getAPluginContext(name: String, base: Context): AContext {
        var aPluginContext = aPluginContextMap[name]
        if (aPluginContext == null) {
            aPluginContext = AContext(base, this, armour)
            aPluginContextMap.put(name, aPluginContext)
        }
        return aPluginContext
    }
}