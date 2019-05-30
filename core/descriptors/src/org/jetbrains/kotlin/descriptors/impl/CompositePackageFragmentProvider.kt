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

package org.jetbrains.kotlin.descriptors.impl

import org.jetbrains.kotlin.descriptors.PackageFragmentDescriptor
import org.jetbrains.kotlin.descriptors.PackageFragmentProvider
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import java.util.*

interface SmartEmptyPackageFragmentProvider

class CompositePackageFragmentProvider(// can be modified from outside
    val providers: List<PackageFragmentProvider>
) : PackageFragmentProvider {

    override fun getPackageFragments(fqName: FqName): List<PackageFragmentDescriptor> {
        val result = ArrayList<PackageFragmentDescriptor>()
        for (provider in providers) {
            result.addAll(provider.getPackageFragments(fqName))
        }
        return result.toList()
    }

    override fun isEmpty(fqName: FqName): Boolean {
        for (provider in providers) {
            if (!provider.isEmpty(fqName)) return false
        }
        return true
    }

    override fun getSubPackagesOf(fqName: FqName, nameFilter: (Name) -> Boolean): Collection<FqName> {
        val result = HashSet<FqName>()
        for (provider in providers) {
            result.addAll(provider.getSubPackagesOf(fqName, nameFilter))
        }
        return result
    }
}
