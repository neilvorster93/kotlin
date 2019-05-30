/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.container

import com.intellij.util.containers.ContainerUtil
import java.lang.reflect.*

interface ValueDescriptor {
    fun getValue(): Any
}

abstract class ComponentDescriptor() : ValueDescriptor {
    abstract fun getRegistrations(): Iterable<Type>
    abstract fun getDependencies(context: ValueResolveContext): Collection<Type>
    open val shouldInjectProperties: Boolean
        get() = false

    val types = ContainerUtil.newConcurrentSet<Type>()
}

class IterableDescriptor(val descriptors: Iterable<ValueDescriptor>) : ValueDescriptor {
    override fun getValue(): Any {
        return descriptors.map { it.getValue() }
    }

    override fun toString(): String = "Iterable: $descriptors"
}