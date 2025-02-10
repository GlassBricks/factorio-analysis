# Factorio analysis

Calculates optimal Factorio factory production, like FactorioLab, but better on:

- Can consider multiple ways to produce a recipe at the same time: which machines, modules, etc. to use
- Can customize the LP a lot, allowing you to add other bells and whistles (processes, variables, and constraints)
- More constraints, like "I have only 8 quality 3 modules"
- Can optimize multiple factories together in stages, with constraints connecting them: E.g. mall 1 will must produce in
  30 minutes all the modules that mall 2 uses.
- Hackable
- Export to graphviz graph
- Very rudimentary export as blueprint

All the above makes it particularly good for optimal quality upcycling builds.
See the runnable stuff in `/scripts/src`, or `ProblemTest.kt` for examples.

Currently just API, no GUI or CLI.

Still todo:

- Export as blueprint that sucks less
- Logistics modelling (tagging ingredients by location)
- Planet requirements, multi-planet optimization, space logistics modelling
- Power, fuel, nutrient requirements
- More granular machine+recipe config
- Better mod support?
