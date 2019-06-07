/*
 * Copyright 2010-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm.lower

import org.jetbrains.kotlin.backend.jvm.JvmBackendContext
import org.jetbrains.kotlin.ir.backend.jvm.lower.serialization.ir.JvmIrModuleSerializer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFile

fun serializeIrFile(context: JvmBackendContext, irFile: IrFile): ByteArray {
    return JvmIrModuleSerializer(
        context,
        context.declarationTable,
        context.psiSourceManager,
        externallyVisibleOnly = true
    ).serializeJvmIrFile(irFile).toByteArray()
}

fun serializeToplevelIrClass(context: JvmBackendContext, irClass: IrClass): ByteArray {
    assert(irClass.parent is IrFile)
    return JvmIrModuleSerializer(
        context,
        context.declarationTable,
        context.psiSourceManager,
        externallyVisibleOnly = true
    ).serializeJvmToplevelClass(irClass)
        .toByteArray()
}