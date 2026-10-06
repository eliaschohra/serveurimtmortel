package com.mojang.brigadier.context; public class CommandContext<S> { public S getSource(){return null;} public <V> V getArgument(String n, Class<V> c){return null;} }
