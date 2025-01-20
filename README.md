# Factorio analysis

Calculates optimal Factorio factory production, like FactorioLab, but better on:

- Can customize the LP a lot, allowing you to almost add any other bells and whistles (variables and constraints)
- Can have constraints based on build materials, not just input/output
- ^ makes it actually good for quality upcycling builds
- Hackable (can mess with the code)

Runnable stuff is in `/scripts/src`.
For example usages, see there or `/factorio-recipe-analysis/src/test/kotlin/ProblemTest.kt`

Currently only API, no GUI or CLI.

Still todo:

- Custom recipes/LP augmentations
- Power, fuel, nutrient requirements
- More granular machine+recipe config
- ILP stuff
- Chaining builds. For example: build 1 is a mall, build 2 does final product, but build 2 must be buildable by build 1
  in X minutes
    - To answer what's the fastest quality-quality modules upscaling strategy?
