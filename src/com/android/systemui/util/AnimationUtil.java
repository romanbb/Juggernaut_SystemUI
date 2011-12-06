package com.android.systemui.util;

public class AnimationUtil 
{
	public static int quadraticOutEase(float currentTime, float startValue, float changeInValue, float duration) 
	{
		currentTime /= duration;
		int returnValue =  (int) (-changeInValue * currentTime*(currentTime-2) + startValue);
	
		return returnValue;
	}
}
