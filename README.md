# JavaScript Dead Code Elimination tools
This repository contains several JavaScript dead code elimination tools:

+ [basic](basic/README.md): Dynamic Node.js script that removes dead code by marking all functions, loading the web application and removing uncalled functions.
+ [static](static/README.md): Node.js script that uses static analysis to generates a call graph to remove uncalled functions.
+ [dynamic](dynamic/README.md): Updated and more stable version of the basic script.
+ [hybrid](hybrid/README.md): Dead code elimination tool that combines static and dynamic approaches, and allows for custom heuristics.
