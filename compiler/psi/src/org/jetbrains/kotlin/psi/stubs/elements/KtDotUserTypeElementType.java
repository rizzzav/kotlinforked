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

package org.jetbrains.kotlin.psi.stubs.elements;

import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtDotUserType;
import org.jetbrains.kotlin.psi.stubs.KotlinDotUserTypeStub;
import org.jetbrains.kotlin.psi.stubs.impl.KotlinDotUserTypeStubImpl;

import java.io.IOException;

import static org.jetbrains.kotlin.psi.stubs.elements.TypeBeanSerializationKt.*;

public class KtDotUserTypeElementType extends KtStubElementType<KotlinDotUserTypeStub, KtDotUserType> {
    public KtDotUserTypeElementType(@NotNull @NonNls String debugName) {
        super(debugName, KtDotUserType.class, KotlinDotUserTypeStub.class);
    }

    @NotNull
    @Override
    public KotlinDotUserTypeStub createStub(@NotNull KtDotUserType psi, StubElement parentStub) {
        return new KotlinDotUserTypeStubImpl((StubElement<?>) parentStub, null, null);
    }

    @Override
    public void serialize(@NotNull KotlinDotUserTypeStub stub, @NotNull StubOutputStream dataStream) throws IOException {
        KotlinDotUserTypeStubImpl stubImpl = (KotlinDotUserTypeStubImpl) stub;
        serializeTypeBean(dataStream, stubImpl.getUpperBound());
        serializeTypeBean(dataStream, stubImpl.getAbbreviatedType());
    }

    @NotNull
    @Override
    public KotlinDotUserTypeStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
        return new KotlinDotUserTypeStubImpl((StubElement<?>) parentStub,
                                             deserializeTypeBean(dataStream),
                                             deserializeClassTypeBean(dataStream));
    }
}
