package com.github.xyzboom.bf.ksp

import com.github.xyzboom.bf.def.DefinitionDecl

const val extra = """
builtin:
  no-parent:
    - classKind
"""

@DefinitionDecl(def, extra)
const val def = """
// declaration
prog: topDecl+;
topDecl: _topDecl lang;
lang;
_topDecl: class | field | func;
    
class: classKind declName typeParam superType? superIntfList memberDecl+;
classKind;
superIntfList: superType*;
    
memberDecl: memberMethod; // others todo
memberMethod: declName param* type override*;
    
// override
override: memberMethod;
    
param: declName type;
    
type: typeParam | superType;
    
superType: class typeArg*;
// leaf
declName;
typeParam;
typeArg;
field; // todo
func; // todo
"""