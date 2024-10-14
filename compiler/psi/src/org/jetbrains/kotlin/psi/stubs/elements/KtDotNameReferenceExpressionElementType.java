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
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtDotNameReferenceExpression;
import org.jetbrains.kotlin.psi.stubs.KotlinDotNameReferenceExpressionStub;
import org.jetbrains.kotlin.psi.stubs.impl.KotlinDotNameReferenceExpressionStubImpl;

import java.io.IOException;

public class KtDotNameReferenceExpressionElementType extends KtStubElementType<KotlinDotNameReferenceExpressionStub, KtDotNameReferenceExpression> {
    public KtDotNameReferenceExpressionElementType(@NotNull @NonNls String debugName) {
        super(debugName, KtDotNameReferenceExpression.class, KotlinDotNameReferenceExpressionStub.class);
    }

    @Override
    public KotlinDotNameReferenceExpressionStub createStub(@NotNull KtDotNameReferenceExpression psi, StubElement parentStub) {
        return new KotlinDotNameReferenceExpressionStubImpl(parentStub, StringRef.fromString(psi.getReferencedName()));
    }

    @Override
    public void serialize(@NotNull KotlinDotNameReferenceExpressionStub stub, @NotNull StubOutputStream dataStream) throws IOException {
        dataStream.writeName(stub.getReferencedName());
        dataStream.writeBoolean(
                stub instanceof KotlinDotNameReferenceExpressionStubImpl && ((KotlinDotNameReferenceExpressionStubImpl) stub).isClassRef());
    }

    @NotNull
    @Override
    public KotlinDotNameReferenceExpressionStub deserialize(@NotNull StubInputStream dataStream, StubElement parentStub) throws IOException {
        StringRef referencedName = dataStream.readName();
        boolean isClassRef = dataStream.readBoolean();
        return new KotlinDotNameReferenceExpressionStubImpl(parentStub, referencedName, isClassRef);
    }
}
