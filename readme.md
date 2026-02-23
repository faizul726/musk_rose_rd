# Musk Rose RD - Shaders dedicated for beauty.

A shader pack for RenderDragon.

![Screenshot](./images/main.png)

## Download

See [Releases](https://github.com/Rinloid/musk_rose_rd/releases)

## Supported platforms

* Windows
* Android

## Installations

* [Windows](https://youtu.be/2HbDrs2LZ58)
* [Android](https://youtu.be/MYlnjqnFBgw)

Thanks to [ENDERMANYK](https://twitter.com/intent/user?user_id=974631821223890945) for the tutorial.

## Building
1. Download [shaderc binary](https://github.com/devendrn/newb-shader/releases/tag/dev) and put in source root with filename `shaderc/shaderc.exe`

2. Install lazurite using pip

```
python -m pip install lazurite==0.8.3
```

3. Build

```
python -m pip lazurite build src -p <android|ios|windows|merged>
```

Output .material.bin files will be in source root.
