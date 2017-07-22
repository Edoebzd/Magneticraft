package com.cout970.magneticraft.tileentity

import com.cout970.magneticraft.Debug
import com.cout970.magneticraft.IVector3
import com.cout970.magneticraft.api.energy.IElectricNode
import com.cout970.magneticraft.api.energy.IElectricNodeHandler
import com.cout970.magneticraft.api.internal.energy.ElectricNode
import com.cout970.magneticraft.api.internal.energy.WireConnectorWrapper
import com.cout970.magneticraft.block.ElectricMachines
import com.cout970.magneticraft.config.Config
import com.cout970.magneticraft.misc.ElectricConstants
import com.cout970.magneticraft.misc.block.get
import com.cout970.magneticraft.misc.block.getFacing
import com.cout970.magneticraft.misc.block.getOrientation
import com.cout970.magneticraft.misc.crafting.FurnaceCraftingProcess
import com.cout970.magneticraft.misc.inventory.InventoryCapabilityFilter
import com.cout970.magneticraft.misc.render.RenderCache
import com.cout970.magneticraft.misc.tileentity.RegisterTileEntity
import com.cout970.magneticraft.misc.world.isClient
import com.cout970.magneticraft.misc.world.isServer
import com.cout970.magneticraft.registry.ELECTRIC_NODE_HANDLER
import com.cout970.magneticraft.registry.FORGE_ENERGY
import com.cout970.magneticraft.registry.fromTile
import com.cout970.magneticraft.registry.getOrNull
import com.cout970.magneticraft.tileentity.core.TileBase
import com.cout970.magneticraft.tileentity.modules.*
import com.cout970.magneticraft.tilerenderer.core.PIXEL
import com.cout970.magneticraft.util.interpolate
import com.cout970.magneticraft.util.vector.*
import net.minecraft.util.EnumFacing
import net.minecraft.util.ITickable
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

/**
 * Created by cout970 on 2017/06/29.
 */

@RegisterTileEntity("connector")
class TileConnector : TileBase(), ITickable {

    val node = ElectricNode(container.ref, capacity = 1.0)
    val wrapper = WireConnectorWrapper(node, this::getConnectors)

    val electricModule = ModuleElectricity(
            electricNodes = listOf(wrapper),
            onWireChange = { if (world.isClient) wireRender.reset() },
            maxWireDistance = 10.0,
            canConnectAtSide = this::canConnectAtSide,
            connectableDirections = this::getConnectableDirections
    )

    val facing: EnumFacing get() = getBlockState().getFacing()

    // client
    val wireRender = RenderCache()
    var hasBase: Boolean = false

    init {
        initModules(electricModule)
    }

    override fun update() {
        super.update()
        if (world.isServer) {
            if (node.voltage > ElectricConstants.TIER_1_MACHINES_MIN_VOLTAGE) {
                val tile = world.getTileEntity(pos.offset(facing.opposite))
                val handler = tile?.getOrNull(FORGE_ENERGY, facing)
                if (handler != null) {
                    val amount = (interpolate(node.voltage, ElectricConstants.TIER_1_MACHINES_MIN_VOLTAGE,
                            ElectricConstants.TIER_1_MAX_VOLTAGE) * 400).toInt()
                    val accepted = Math.min(
                            handler.receiveEnergy(amount, true),
                            node.applyPower(-amount.toDouble(), true).toInt()
                    )
                    if (accepted > 0) {
                        handler.receiveEnergy(accepted, false)
                        node.applyPower(-accepted.toDouble(), false)
                    }
                }
            }
        }
        if (Debug.DEBUG) {
            sendUpdateToNearPlayers()
        }
    }

    override fun onBlockStateUpdates() {
        if (world.isClient) {
            hasBase = shouldHaveBase(this)
        }
    }

    fun shouldHaveBase(te: TileConnector): Boolean {
        val tile = te.world.getTileEntity(te.pos.offset(te.facing.opposite))
        if (tile != null) {
            val handler = ELECTRIC_NODE_HANDLER!!.fromTile(tile, te.facing)
            if (handler is IElectricNodeHandler) {
                val node = handler.nodes.firstOrNull { it is IElectricNode }
                if (node != null && handler.canConnect(node as IElectricNode, te.electricModule, te.wrapper,
                        te.facing)) {
                    return false
                }
            }
        }
        return true
    }

    fun getConnectableDirections(): List<Pair<BlockPos, EnumFacing>> {
        return if (facing.opposite.axisDirection == EnumFacing.AxisDirection.NEGATIVE) {
            listOf(
                    facing.opposite.toBlockPos() to facing,
                    facing.opposite.toBlockPos() * 2 to facing)
        } else emptyList()
    }

    fun getConnectors(): List<IVector3> {
        val offset = facing.opposite.directionVec.toVec3d() * PIXEL * 3.0
        return listOf(vec3Of(0.5) + offset)
    }

    fun canConnectAtSide(facing: EnumFacing?): Boolean {
        return facing == null || facing == this.facing.opposite
    }

    override fun getRenderBoundingBox(): AxisAlignedBB = INFINITE_EXTENT_AABB
}

@RegisterTileEntity("battery")
class TileBattery : TileBase(), ITickable {

    val facing: EnumFacing get() = getBlockState().getOrientation()
    val node = ElectricNode(container.ref, capacity = 8.0)

    val electricModule = ModuleElectricity(
            electricNodes = listOf(node),
            canConnectAtSide = this::canConnectAtSide
    )
    val storageModule = ModuleInternalStorage(
            capacity = Config.blockBatteryCapacity,
            mainNode = node,
            maxChargeSpeed = 640.0,
            upperVoltageLimit = ElectricConstants.TIER_1_BATTERY_CHARGE_VOLTAGE,
            lowerVoltageLimit = ElectricConstants.TIER_1_BATTERY_DISCHARGE_VOLTAGE
    )
    val invModule = ModuleInventory(2)

    val itemChargeModule = ModuleChargeItems(
            invModule = invModule,
            storage = storageModule,
            chargeSlot = 0,
            dischargeSlot = 1,
            transferRate = Config.blockBatteryTransferRate
    )

    init {
        initModules(electricModule, storageModule, invModule, itemChargeModule)
    }

    override fun update() {
        super.update()
    }

    fun canConnectAtSide(facing: EnumFacing?): Boolean {
        return facing == this.facing.opposite
    }
}

@RegisterTileEntity("electric_furnace")
class TileElectricFurnace : TileBase(), ITickable {

    val facing: EnumFacing get() = getBlockState().getOrientation()
    val node = ElectricNode(container.ref)

    val electricModule = ModuleElectricity(
            electricNodes = listOf(node)
    )
    val storageModule = ModuleInternalStorage(
            capacity = 10000,
            mainNode = node
    )
    val invModule = ModuleInventory(2, capabilityFilter = {
        InventoryCapabilityFilter(it, inputSlots = listOf(0), outputSlots = listOf(1))
    })
    val processModule = ModuleElectricProcessing(
            craftingProcess = FurnaceCraftingProcess(invModule, 0, 1),
            storage = storageModule,
            workingRate = 1f,
            costPerTick = Config.electricFurnaceMaxConsumption.toFloat()
    )

    init {
        initModules(electricModule, storageModule, invModule, processModule)
    }

    override fun update() {
        super.update()
    }
}

@RegisterTileEntity("electric_pole")
class TileElectricPole : TileBase(), ITickable {
    val node = ElectricNode(container.ref, capacity = 1.0)
    val wrapper = WireConnectorWrapper(node, this::getConnectors)

    val electricModule = ModuleElectricity(
            electricNodes = listOf(wrapper),
            onWireChange = { if (world.isClient) wireRender.reset() },
            canConnectAtSide = { it == null }
    )

    // client
    val wireRender = RenderCache()

    init {
        electricModule.autoConnectWires = true
        initModules(electricModule)
    }

    fun getConnectors(): List<IVector3> {
        val offset = getBlockState()[ElectricMachines.PROPERTY_POLE_ORIENTATION]?.offset ?: return emptyList()
        val first = Vec3d(0.5, 1.0 - 0.0625 * 4, 0.5).add(offset)
        val center = Vec3d(0.5, 1.0, 0.5)
        val last = Vec3d(0.5, 1.0 - 0.0625 * 4, 0.5).subtract(offset)
        return listOf(first, center, last)
    }

    override fun getRenderBoundingBox(): AxisAlignedBB = INFINITE_EXTENT_AABB
}

@RegisterTileEntity("electric_pole_transformer")
class TileElectricPoleTransformer : TileBase(), ITickable {
    val node = ElectricNode(container.ref, capacity = 1.0)
    val wrapper = WireConnectorWrapper(node, this::getConnectors)
    val wrapper2 = WireConnectorWrapper(node, this::getConnectors2)

    val electricModule = ModuleElectricity(
            electricNodes = listOf(wrapper, wrapper2),
            onWireChange = { if (world.isClient) wireRender.reset() },
            canConnectAtSide = { it == null }
    )

    // client
    val wireRender = RenderCache()

    init {
        electricModule.autoConnectWires = true
        initModules(electricModule)
    }

    fun getConnectors(): List<IVector3> {
        val offset = getBlockState()[ElectricMachines.PROPERTY_POLE_ORIENTATION]?.offset ?: return emptyList()
        val first = Vec3d(0.5, 1.0 - 0.0625 * 4, 0.5).add(offset)
        val center = Vec3d(0.5, 1.0, 0.5)
        val last = Vec3d(0.5, 1.0 - 0.0625 * 4, 0.5).subtract(offset)
        return listOf(first, center, last)
    }

    fun getConnectors2(): List<IVector3> {
        val angle = getBlockState()[ElectricMachines.PROPERTY_POLE_ORIENTATION]?.offset ?: return emptyList()
        val offset = angle.rotateYaw(Math.toRadians(-90.0).toFloat())
        val vec = Vec3d(0.5, 1.0 - PIXEL * 6.5, 0.5).add(offset * 0.5)
        return listOf(vec)
    }

    override fun getRenderBoundingBox(): AxisAlignedBB = INFINITE_EXTENT_AABB
}

