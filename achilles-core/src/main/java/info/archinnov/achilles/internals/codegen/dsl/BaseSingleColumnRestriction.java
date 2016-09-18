/*
 * Copyright (C) 2012-2016 DuyHai DOAN
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

package info.archinnov.achilles.internals.codegen.dsl;

import static info.archinnov.achilles.internals.codegen.dsl.AbstractDSLCodeGen.relationToSymbolForJavaDoc;
import static info.archinnov.achilles.internals.parser.TypeUtils.*;
import static info.archinnov.achilles.internals.utils.NamingHelper.upperCaseFirst;

import javax.lang.model.element.Modifier;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeName;

import info.archinnov.achilles.internals.codegen.dsl.AbstractDSLCodeGen.FieldSignatureInfo;
import info.archinnov.achilles.internals.codegen.dsl.AbstractDSLCodeGen.ReturnType;

public interface BaseSingleColumnRestriction {

    default MethodSpec buildColumnRelation(String relation, TypeName nextType, FieldSignatureInfo fieldInfo, ReturnType returnType) {
        final String methodName = upperCaseFirst(relation);
        final MethodSpec.Builder builder = MethodSpec.methodBuilder(methodName)
                .addJavadoc("Generate a SELECT ... FROM ... WHERE ... <strong>$L $L ?</strong>", fieldInfo.quotedCqlColumn, relationToSymbolForJavaDoc(relation))
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "static-access").build())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addParameter(fieldInfo.typeName, fieldInfo.fieldName)
                .addStatement("where.and($T.$L($S, $T.bindMarker($S)))",
                        QUERY_BUILDER, relation, fieldInfo.quotedCqlColumn, QUERY_BUILDER, fieldInfo.quotedCqlColumn)
                .addStatement("boundValues.add($N)", fieldInfo.fieldName)
                .addStatement("encodedValues.add(meta.$L.encodeFromJava($N))", fieldInfo.fieldName, fieldInfo.fieldName)
                .returns(nextType);

        if (returnType == ReturnType.NEW) {
            builder.addStatement("return new $T(where)", nextType);
        } else {
            builder.addStatement("return $T.this", nextType);
        }

        return builder.build();
    }

    default MethodSpec buildColumnInVarargs(TypeName nextType, FieldSignatureInfo fieldInfo, ReturnType returnType) {
        final String methodName = "IN";
        final String param = fieldInfo.fieldName;
        final TypeName paramTypeName = fieldInfo.typeName;
        final MethodSpec.Builder builder = MethodSpec.methodBuilder(methodName)
                .addJavadoc("Generate a SELECT ... FROM ... WHERE ... <strong>$L IN ?</strong>", fieldInfo.quotedCqlColumn)
                .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class).addMember("value", "$S", "static-access").build())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addParameter(ArrayTypeName.of(paramTypeName), param)
                .varargs()
                .addStatement("$T.validateTrue($T.isNotEmpty($L), \"Varargs for field '%s' should not be null/empty\", $S)",
                        VALIDATOR, ARRAYS_UTILS, fieldInfo.fieldName, fieldInfo.fieldName)
                .addStatement("where.and($T.in($S,$T.bindMarker($S)))",
                        QUERY_BUILDER, fieldInfo.quotedCqlColumn, QUERY_BUILDER, fieldInfo.quotedCqlColumn);

        if (paramTypeName.isPrimitive()) {
            builder.addStatement("final $T varargs = $T.<Object>asList(($T[])$L)", LIST_OBJECT, ARRAYS, paramTypeName, param)
                    .addStatement("final $T encodedVarargs = $T.stream(($T[])$L).mapToObj(x -> meta.$L.encodeFromJava(x)).collect($T.toList())",
                            LIST_OBJECT, ARRAYS, paramTypeName, param, param, COLLECTORS);
        } else {
            builder.addStatement("final $T varargs = $T.<Object>asList((Object[])$L)", LIST_OBJECT, ARRAYS, param)
                    .addStatement("final $T encodedVarargs = $T.<$T>stream(($T[])$L).map(x -> meta.$L.encodeFromJava(x)).collect($T.toList())",
                            LIST_OBJECT, ARRAYS, paramTypeName, paramTypeName, param, param, COLLECTORS);
        }

        builder.addStatement("boundValues.add(varargs)")
                .addStatement("encodedValues.add(encodedVarargs)")
                .returns(nextType);

        if (returnType == ReturnType.NEW) {
            builder.addStatement("return new $T(where)", nextType);
        } else {
            builder.addStatement("return $T.this", nextType);
        }

        return builder.build();
    }
}
