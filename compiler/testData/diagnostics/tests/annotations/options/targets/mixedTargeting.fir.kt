// ISSUE: KT-69058

// FILE: test/AnnotationTargets.java

package test;

import java.lang.annotation.*;

public class AnnotationTargets {

    @kotlin.annotation.Target(allowedTargets = kotlin.annotation.AnnotationTarget.TYPE)
    public @interface baseButKotlinType {
        
    }
    
    @Target({})
    @kotlin.annotation.Target(allowedTargets = kotlin.annotation.AnnotationTarget.CLASS)
    public @interface noneButKotlinClass {

    }

    @Target(ElementType.ANNOTATION_TYPE)
    @kotlin.annotation.Target(allowedTargets = kotlin.annotation.AnnotationTarget.FUNCTION)
    public @interface metaButKotlinFun {

    }

    @Target(ElementType.CONSTRUCTOR)
    @kotlin.annotation.Target(allowedTargets = kotlin.annotation.AnnotationTarget.PROPERTY_GETTER)
    public @interface konstructorButKotlinGetter {

    }

    @Target(ElementType.FIELD)
    @kotlin.annotation.Target(allowedTargets = kotlin.annotation.AnnotationTarget.ANNOTATION_CLASS)
    public @interface fieldannButKotlinMeta {

    }


    @Target(ElementType.LOCAL_VARIABLE)
    @kotlin.annotation.Target(allowedTargets = {})
    public @interface localButKotlinNone {

    }

    @Target(ElementType.METHOD)
    @kotlin.annotation.Target(allowedTargets = {kotlin.annotation.AnnotationTarget.CLASS, kotlin.annotation.AnnotationTarget.LOCAL_VARIABLE})
    public @interface methodButKotlinMultiple {

    }

    @Target(ElementType.PARAMETER)
    @kotlin.annotation.Target(allowedTargets = kotlin.annotation.AnnotationTarget.FILE)
    public @interface parameterButKotlinFile {

    }


    @Target(ElementType.TYPE)
    @kotlin.annotation.Target(allowedTargets = kotlin.annotation.AnnotationTarget.VALUE_PARAMETER)
    public @interface typeButKotlinParameter {

    }

    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
    @kotlin.annotation.Target(allowedTargets = kotlin.annotation.AnnotationTarget.PROPERTY)
    public @interface multipleButKotlinProperty {

    }
}

// FILE: test/AnnotationTargets.kt

@file:AnnotationTargets.typeButKotlinParameter
@file:AnnotationTargets.parameterButKotlinFile
package test

import test.AnnotationTargets.*

<!WRONG_ANNOTATION_TARGET!>@baseButKotlinType<!> @metaButKotlinFun @typeButKotlinParameter <!WRONG_ANNOTATION_TARGET!>@konstructorButKotlinGetter<!> @fieldannButKotlinMeta
annotation class KmetaButKotlinFun

@noneButKotlinClass <!WRONG_ANNOTATION_TARGET!>@baseButKotlinType<!> <!WRONG_ANNOTATION_TARGET!>@metaButKotlinFun<!> @typeButKotlinParameter @methodButKotlinMultiple <!WRONG_ANNOTATION_TARGET!>@multipleButKotlinProperty<!> <!WRONG_ANNOTATION_TARGET!>@localButKotlinNone<!>
class KClass(
    <!WRONG_ANNOTATION_TARGET!>@baseButKotlinType<!> @fieldannButKotlinMeta @parameterButKotlinFile @multipleButKotlinProperty val y: @baseButKotlinType <!WRONG_ANNOTATION_TARGET!>@typeButKotlinParameter<!> Int
) {
    <!WRONG_ANNOTATION_TARGET!>@baseButKotlinType<!> @multipleButKotlinProperty @fieldannButKotlinMeta <!WRONG_ANNOTATION_TARGET!>@localButKotlinNone<!>
    val x = 0
        @methodButKotlinMultiple @konstructorButKotlinGetter <!WRONG_ANNOTATION_TARGET!>@typeButKotlinParameter<!> get

    <!WRONG_ANNOTATION_TARGET!>@noneButKotlinClass<!> <!WRONG_ANNOTATION_TARGET!>@baseButKotlinType<!> @methodButKotlinMultiple @multipleButKotlinProperty <!WRONG_ANNOTATION_TARGET!>@konstructorButKotlinGetter<!> @metaButKotlinFun
    fun foo(@parameterButKotlinFile @typeButKotlinParameter i: @baseButKotlinType <!WRONG_ANNOTATION_TARGET!>@multipleButKotlinProperty<!> Int): <!WRONG_ANNOTATION_TARGET!>@fieldannButKotlinMeta<!> <!WRONG_ANNOTATION_TARGET!>@parameterButKotlinFile<!> Int {

        @localButKotlinNone <!WRONG_ANNOTATION_TARGET!>@baseButKotlinType<!> <!WRONG_ANNOTATION_TARGET!>@multipleButKotlinProperty<!> <!WRONG_ANNOTATION_TARGET!>@fieldannButKotlinMeta<!> @methodButKotlinMultiple val j = i + 1
        @baseButKotlinType @multipleButKotlinProperty return j
    }

    <!WRONG_ANNOTATION_TARGET!>@baseButKotlinType<!> <!WRONG_ANNOTATION_TARGET!>@methodButKotlinMultiple<!> @konstructorButKotlinGetter
    constructor(): this(0)
}
