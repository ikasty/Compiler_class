#!/bin/bash

echo "=================="
echo "execute $1"
echo "=================="
echo ""

echo "execute.sh] Make jasmin code..."
java -ea MiniC.MiniC $1.mc

if [ -f $1.j ]
then
    echo ""
    echo "execute.sh] Make class file..."

    jasmin $1.j
    if [ -f $1.class ]
    then
        echo ""
        echo "execute.sh] Try to execute..."

        java -ea -cp . $1
    else
        echo "execute.sh] Could not generate $1.class"
    fi
else
    echo "execute.sh] Could not generate $1.j"
fi