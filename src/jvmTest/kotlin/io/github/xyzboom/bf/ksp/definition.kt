package io.github.xyzboom.bf.ksp

import io.github.xyzboom.bf.def.DefExtra
import io.github.xyzboom.bf.def.DefImplPair
import io.github.xyzboom.bf.def.DefinitionDecl

@DefinitionDecl(
    crossLangFuzzerDef,
    extra = DefExtra(
        noParentNames = [
            "classKind",
            "declName",
            "typeParamName",
            "lang",
        ],
        noCacheNames = [
            "classKind",
            "declName",
            "typeParamName",
            "lang",
        ],
        implNames = [
            DefImplPair("prog", IrProgram::class),
        ]
    )
)
const val crossLangFuzzerDef = """
// declaration
prog: topDecl+;
topDecl: _topDecl lang;
lang;
_topDecl: class | field | func;

class: classKind declName typeParam* superType? superIntfList memberDecl+;
classKind;
superIntfList: superType*;

memberDecl: memberMethod; // others todo
memberMethod: declName param* type override*;

// override
override: memberMethod;

param: declName type;

type: typeParam | superType;
typeParam: typeParamName;

superType: class typeArg*;
// leaf
declName;
typeParamName;
typeArg;
field; // todo
func; // todo
"""