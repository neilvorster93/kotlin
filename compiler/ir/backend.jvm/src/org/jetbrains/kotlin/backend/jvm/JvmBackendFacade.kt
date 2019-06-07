/*
 * Copyright 2010-2018 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.backend.jvm

import org.jetbrains.kotlin.backend.common.EmptyLoggingContext
import org.jetbrains.kotlin.backend.common.phaser.PhaseConfig
import org.jetbrains.kotlin.backend.jvm.lower.serializeIrFile
import org.jetbrains.kotlin.backend.jvm.lower.serializeToplevelIrClass
import org.jetbrains.kotlin.codegen.CompilationErrorHandler
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.ir.backend.jvm.lower.serialization.ir.JvmIrDeserializer
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.MetadataSource
import org.jetbrains.kotlin.ir.util.ExternalDependenciesGenerator
import org.jetbrains.kotlin.ir.util.SymbolTable
import org.jetbrains.kotlin.ir.util.IrDeserializer
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi2ir.Psi2IrTranslator
import org.jetbrains.kotlin.psi2ir.PsiSourceManager
import org.jetbrains.kotlin.psi2ir.generators.GeneratorContext

object JvmBackendFacade {
    fun doGenerateFiles(
        files: Collection<KtFile>,
        state: GenerationState,
        errorHandler: CompilationErrorHandler,
        phaseConfig: PhaseConfig
    ) {
        val psi2ir = Psi2IrTranslator(state.languageVersionSettings)
        val psi2irContext = psi2ir.createGeneratorContext(state.module, state.bindingContext, extensions = JvmGeneratorExtensions)
        val deserializer = JvmIrDeserializer(state.module, EmptyLoggingContext, psi2irContext.irBuiltIns, psi2irContext.symbolTable, state.languageVersionSettings)
        val irModuleFragment = psi2ir.generateModuleFragment(psi2irContext, files, deserializer)

        doGenerateFilesInternal(state, errorHandler, irModuleFragment, psi2irContext, phaseConfig, deserializer)
    }

    internal fun doGenerateFilesInternal(
        state: GenerationState,
        errorHandler: CompilationErrorHandler,
        irModuleFragment: IrModuleFragment,
        psi2irContext: GeneratorContext,
        phaseConfig: PhaseConfig,
        deserializer: IrDeserializer?
    ) {
        doGenerateFilesInternal(
            state, errorHandler, irModuleFragment, psi2irContext.symbolTable, psi2irContext.sourceManager, phaseConfig, deserializer
        )
    }

    internal fun doGenerateFilesInternal(
        state: GenerationState,
        errorHandler: CompilationErrorHandler,
        irModuleFragment: IrModuleFragment,
        symbolTable: SymbolTable,
        sourceManager: PsiSourceManager,
        phaseConfig: PhaseConfig,
        deserializer: IrDeserializer? = null,
        firMode: Boolean = false
    ) {
        val jvmBackendContext = JvmBackendContext(
            state, sourceManager, irModuleFragment.irBuiltins, irModuleFragment, symbolTable, phaseConfig, firMode
        )
        //TODO
        ExternalDependenciesGenerator(
            irModuleFragment.descriptor,
            symbolTable,
            irModuleFragment.irBuiltins,
            JvmGeneratorExtensions.externalDeclarationOrigin,
            deserializer
        ).generateUnboundSymbolsAsDependencies()

        for (irFile in irModuleFragment.files) {
            irFile.metadata!!.serializedIr = serializeIrFile(jvmBackendContext, irFile)
            for (irClass in irFile.declarations.filterIsInstance<IrClass>()) {
                (irClass.metadata as MetadataSource.Class).serializedIr = serializeToplevelIrClass(jvmBackendContext, irClass)
            }
        }

        val jvmBackend = JvmBackend(jvmBackendContext)

        for (irFile in irModuleFragment.files) {
            try {
                jvmBackend.lowerFile(irFile)
            } catch (e: Throwable) {
                errorHandler.reportException(e, null) // TODO ktFile.virtualFile.url
            }
        }

        for (irFile in irModuleFragment.files) {
            try {
                jvmBackend.generateLoweredFile(irFile)
                state.afterIndependentPart()
            } catch (e: Throwable) {
                errorHandler.reportException(e, null)
            }
        }
    }
}
