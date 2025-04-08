lexer grammar BaseFuzzerLexer;

OR: '|';
COLON: ':';
SEMI : ';';
TILDE: '~';
QUESTION: '?';
EXCL: '!';
STAR: '*';
ADD : '+';

LINE_COMMENT: '//' ~[\r\n]* -> skip;
ID: [a-zA-Z_][a-zA-Z_0-9]*;
WS: [ \t\n\r\f]+ -> skip ;