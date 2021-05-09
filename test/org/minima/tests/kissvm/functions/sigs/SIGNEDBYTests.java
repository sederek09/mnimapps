package org.minima.tests.kissvm.functions.sigs;

import org.minima.kissvm.functions.sigs.SIGNEDBY;

import org.minima.kissvm.Contract;
import org.minima.kissvm.exceptions.ExecutionException;
import org.minima.kissvm.exceptions.MinimaParseException;
import org.minima.kissvm.expressions.ConstantExpression;
import org.minima.kissvm.functions.MinimaFunction;
import org.minima.kissvm.values.BooleanValue;
import org.minima.kissvm.values.HEXValue;
import org.minima.kissvm.values.NumberValue;
import org.minima.kissvm.values.ScriptValue;
import org.minima.kissvm.values.Value;
import org.minima.objects.Transaction;
import org.minima.objects.Witness;
import org.minima.objects.base.MiniData;
import org.minima.objects.base.MiniNumber;
import org.minima.objects.keys.MultiKey;

import java.util.ArrayList;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

//BooleanValue SIGNEDBY  (HEXValue pubkey)
public class SIGNEDBYTests {

    @Test
    public void testConstructors() {
        SIGNEDBY fn = new SIGNEDBY();
        MinimaFunction mf = fn.getNewFunction();

        assertEquals("SIGNEDBY", mf.getName());
        assertEquals(0, mf.getParameterNum());

        try {
            mf = MinimaFunction.getFunction("SIGNEDBY");
            assertEquals("SIGNEDBY", mf.getName());
            assertEquals(0, mf.getParameterNum());
        } catch (MinimaParseException ex) {
            fail();
        }
    }

    @Test
    public void testValidParams() {
        // SIGNEDBY is actually string search in a list of signatures
        // So we can simplify test without actual keys
        MiniData SingleSig1 = MiniData.getRandomData(20);
        MiniData SingleSig2 = MiniData.getRandomData(20);

        SIGNEDBY fn = new SIGNEDBY();

        {
            Contract ctr = new Contract("", SingleSig1.toString(), new Witness(), new Transaction(), new ArrayList<>());
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new HEXValue(SingleSig1)));
            try {
                Value res = mf.runFunction(ctr);
                assertEquals(Value.VALUE_BOOLEAN, res.getValueType());
                assertEquals("TRUE", ((BooleanValue) res).toString());
            } catch (ExecutionException ex) {
                fail();
            }
        }
        {
            Contract ctr = new Contract("", SingleSig1.toString(), new Witness(), new Transaction(), new ArrayList<>());
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new HEXValue(SingleSig2)));
            try {
                Value res = mf.runFunction(ctr);
                assertEquals(Value.VALUE_BOOLEAN, res.getValueType());
                assertEquals("FALSE", ((BooleanValue) res).toString());
            } catch (ExecutionException ex) {
                fail();
            }
        }
    }

    @Test
    public void testInvalidParams() {
        Contract ctr = new Contract("", "", new Witness(), new Transaction(), new ArrayList<>());

        SIGNEDBY fn = new SIGNEDBY();

        // Invalid param count
        {
            MinimaFunction mf = fn.getNewFunction();
            assertThrows(ExecutionException.class, () -> {
                Value res = mf.runFunction(ctr);
            });
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new HEXValue("0x01234567")));
            mf.addParameter(new ConstantExpression(new HEXValue("0x01234567")));
            assertThrows(ExecutionException.class, () -> {
                Value res = mf.runFunction(ctr);
            });
        }

        // Invalid param domain
        {
        }

        // Invalid param types
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new BooleanValue(true)));
            assertThrows(ExecutionException.class, () -> {
                Value res = mf.runFunction(ctr);
            });
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new NumberValue(1)));
            assertThrows(ExecutionException.class, () -> {
                Value res = mf.runFunction(ctr);
            });
        }
        {
            MinimaFunction mf = fn.getNewFunction();
            mf.addParameter(new ConstantExpression(new ScriptValue("Hello World")));
            assertThrows(ExecutionException.class, () -> {
                Value res = mf.runFunction(ctr);
            });
        }
    }
}