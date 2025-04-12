parser grammar BaseFuzzerGrammar;

options { tokenVocab=BaseFuzzerLexer; }

definition: (statement)*;

statement: id (':' statementContentList)? ';';

statementContentList: statementContent ('|' statementContent)*;

statementContent: ref+;

// ID
id: ID;

// Reference
ref: id refType;
refType
    : '!'?      # non_null_ref
    | '?'       # nullable_ref
    | '*'       # zero_or_more_ref
    | '+'       # one_or_more_ref
    ;