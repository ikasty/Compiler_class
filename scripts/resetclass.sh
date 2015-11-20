#! /bin/bash

for i in `echo $CLASSPATH | sed "s\:\ \g"`
do
mv $i/MiniC/MiniC.java.bak $i/MiniC/MiniC.java
mv $i/MiniC/Scanner/Scanner.java.bak $i/MiniC/Scanner/Scanner.java
done

make clean
