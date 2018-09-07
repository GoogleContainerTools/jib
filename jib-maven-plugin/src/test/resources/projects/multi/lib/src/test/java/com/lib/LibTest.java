package com.lib;

import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for simple App.
 */
public class LibTest {
    /** Rigorous Test :-) */
    @Test
    public void testGetThing() {
      Assert.assertEquals("thing", new Lib().getThing());
    }
}
