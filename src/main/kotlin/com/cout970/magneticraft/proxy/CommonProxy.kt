package com.cout970.magneticraft.proxy

import com.cout970.magneticraft.Magneticraft
import com.cout970.magneticraft.gui.GuiHandler
import com.cout970.magneticraft.multiblock.core.MultiblockManager
import com.cout970.magneticraft.network.MessageContainerUpdate
import com.cout970.magneticraft.network.MessageGuiUpdate
import com.cout970.magneticraft.network.MessageTileUpdate
import com.cout970.magneticraft.registry.*
import com.cout970.magneticraft.util.logTime
import com.cout970.magneticraft.world.WorldGenerator
import net.minecraftforge.fml.common.network.NetworkRegistry
import net.minecraftforge.fml.common.registry.ForgeRegistries
import net.minecraftforge.fml.common.registry.GameRegistry
import net.minecraftforge.fml.relauncher.Side

/**
 * This class has the task to initialize the mod, in both sides, client and server
 * See ClientProxy for more tasks executed only in the client
 * See ServerProxy for more tasks executed only in the server, currently nothing
 */
abstract class CommonProxy {

    open fun preInit() {
        // @formatter:off
        logTime("Task initBlocks:")                   { initBlocks(ForgeRegistries.BLOCKS) }
        logTime("Task initItems:")                    { initItems(ForgeRegistries.ITEMS) }
        logTime("Task registerCapabilities:")         { registerCapabilities() }
        logTime("Task initTileEntities:")             { initTileEntities() }
        logTime("Task initFluids:")                   { initFluids() }
        logTime("Task registerOreDictionaryEntries:") { registerOreDictionaryEntries() }
        logTime("Task registerOreGenerations:")       { registerOreGenerations() }
        logTime("Task registerMultiblocks:")          { MultiblockManager.registerDefaults() }
        // @formatter:on
    }

    open fun init() {
        //Init recipes
        logTime("Task registerRecipes:") {
            registerRecipes()
        }

        //World generator
        logTime("Task registerWorldGenerator:") {
            WorldGenerator.init()
            GameRegistry.registerWorldGenerator(WorldGenerator, 10)
        }

        //Gui
        logTime("Task registerGuiHandler:") {
            NetworkRegistry.INSTANCE.registerGuiHandler(Magneticraft, GuiHandler)
        }

        //Network
        //Note for implementing Messages:
        //The class that implements IMessage must have an empty constructor
        logTime("Task registerNetworkMessages:") {
            Magneticraft.network.registerMessage(MessageContainerUpdate.Companion, MessageContainerUpdate::class.java, 0, Side.CLIENT)
            Magneticraft.network.registerMessage(MessageTileUpdate.Companion, MessageTileUpdate::class.java, 1, Side.CLIENT)
            Magneticraft.network.registerMessage(MessageTileUpdate.Companion, MessageTileUpdate::class.java, 2, Side.SERVER)
            Magneticraft.network.registerMessage(MessageGuiUpdate.Companion, MessageGuiUpdate::class.java, 3, Side.SERVER)
        }
    }

    open fun postInit() = Unit

    /**
     * The side of the proxy
     */
    abstract fun getSide(): Side
}