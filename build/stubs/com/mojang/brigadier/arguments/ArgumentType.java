package com.mojang.brigadier.arguments; public interface ArgumentType<T> { T parse(com.mojang.brigadier.StringReader r) throws com.mojang.brigadier.exceptions.CommandSyntaxException; }
