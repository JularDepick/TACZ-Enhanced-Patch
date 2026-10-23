<div align="center">

# Contributing

[[简体中文]](./CONTRIBUTING.md) |
[English]

</div>

Issues and code contributions for TACZ Enhanced Patch are welcome

## Guidelines

### Non-invasive Development

- Interact with TACZ only through its public API and events
- Do not modify TACZ source code, and do not write Mixin or access transformers against TACZ classes
- See [TACZ integration rules](./docs/developer/TACZ接入规范.md) for the full constraints

### Patch Modules

- Each patch must be independently switchable, and the default behavior must match a game without this mod
- Add configuration documentation and developer documentation when introducing a new patch

## How to Contribute

### 1. Submit an Issue

- [Open a new issue](https://github.com/JularDepick/TACZ-Enhanced-Patch/issues/new)
- Describe the symptom, reproduction steps and game environment

### 2. Contribute Code

- [Fork this project](https://github.com/JularDepick/TACZ-Enhanced-Patch/fork)
- Make changes on a fork branch
- [Create a pull request](https://github.com/JularDepick/TACZ-Enhanced-Patch/compare)
- Wait for review
