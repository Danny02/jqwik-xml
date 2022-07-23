/**
 * JLibs: Common Utilities for Java
 * Copyright (C) 2009  Santhosh Kumar T <santhosh.tekuri@gmail.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 */

package jlibs.core.util;

import java.util.Random;

/**
 * @author Santhosh Kumar T
 */
public class RandomUtil {

    private final Random random;

    public RandomUtil(Random random) {
        this.random = random;
    }

    public double random(double min, double max){
        return min+random.nextDouble()*(max-min);
    }

    public float random(float min, float max){
        return (float)(min+random.nextDouble()*(max-min));
    }

    public long random(long min, long max){
        return Math.round(min+random.nextDouble()*(max-min));
    }

    public int random(int min, int max){
        return (int)Math.round(min+random.nextDouble()*(max-min));
    }

    public short random(short min, short max){
        return (short)Math.round(min+random.nextDouble()*(max-min));
    }

    public byte random(byte min, byte max){
        return (byte)Math.round(min+random.nextDouble()*(max-min));
    }

    public boolean randomBoolean(){
        return random.nextDouble()<0.5d;
    }

    public boolean randomBoolean(Boolean bool){
        if(Boolean.TRUE.equals(bool))
            return true;
        else if(Boolean.FALSE.equals(bool))
            return false;
        else // random either 0 or 1
            return randomBoolean();
    }
}
