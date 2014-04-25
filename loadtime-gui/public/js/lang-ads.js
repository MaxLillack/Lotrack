PR['registerLangHandler'](
    PR['createSimpleLexer'](
        [
         // Whitespace
         [PR['PR_PLAIN'],       /^[\t\n\r \xA0]+/, null, '\t\n\r \xA0'],
         
         // A double quoted, possibly multi-line, string.
         [PR['PR_STRING'],      /^\"(?:[^\"\\]|\\[\s\S])*(?:\"|$)/, null, '"'],
         
         // comment.llvm
         [PR['PR_COMMENT'],       /^;[^\r\n]*/, null, ';'],
        ],
        [
         // llvm instructions
         [PR['PR_KEYWORD'],     /CMP|PPARM/, null],
         
         // variable.llvm
         [PR['PR_TYPE'],       /^\s(?:[%@][-a-zA-Z$._][-a-zA-Z$._0-9]*)/],
         
         // variable.language.llvm
         [PR['PR_TYPE'],       /^\s(?:[%]\d+)/],
         
         // storage.type.language.llvm
         [PR['PR_PLAIN'],       /^\b(?:i\d+\**)/],
         
         // variable.metadata.llvm
         [PR['PR_PLAIN'],       /^(!\d+)/],
         
         // constant.numeric.float.llvm
         [PR['PR_LITERAL'],       /^\b\d+\.\d+\b/],
         
         // constant.numeric.integer.llvm
         [PR['PR_LITERAL'],       /^\b(?:\d+|0(?:x|X)[a-fA-F0-9]+)\b/],
        ]),
    ['ads', 'mac']);
	
	prettyPrint();