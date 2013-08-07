package org.projectfloodlight.debugevent;

import static org.junit.Assert.*;

import java.util.ArrayList;

import org.junit.Test;
import org.projectfloodlight.debugevent.CircularBuffer;
import org.projectfloodlight.test.FloodlightTestCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CircularBufferTest extends FloodlightTestCase {
    CircularBuffer<String> cb;
    protected static Logger log = LoggerFactory.getLogger(CircularBufferTest.class);

    @Test
    public void testCircularNature() {
        cb = new CircularBuffer<String>(2);
        cb.add("String 1");
        assertEquals(1, cb.size());
        cb.add("String 2");
        assertEquals(2, cb.size());
        cb.add("String 3");
        assertEquals(2, cb.size());

        for (String s : cb) {
            assertEquals(false, s.contains("1"));
        }
    }

    class Elems {
        String str;
        Boolean boo;

        public Elems(String s,boolean b) {
            this.str = s;
            this.boo = b;
        }
    }

    @Test
    public void testAdd() {
        CircularBuffer<Elems> eb = new CircularBuffer<Elems>(2);
        Elems theone = new Elems("String 1", false);
        Elems ret1 = eb.add(theone);
        assertEquals(null, ret1);
        Elems ret2 = eb.add(new Elems("String 2", true));
        assertEquals(null, ret2);
        Elems ret3 = eb.add(new Elems("String 3", true));
        // We want to see if what is returned is a reference to the original object
        // 'theone'. So we use  '==' to compare the references
        assertEquals(true, ret3 == theone);
        log.info("{} {}", ret3, theone);
    }

    @Test
    public void testAddAll() {
        CircularBuffer<Elems> eb = new CircularBuffer<Elems>(2);
        Elems one = new Elems("String 1", false);
        eb.add(one);
        ArrayList<Elems> elist = new ArrayList<Elems>();
        Elems two = new Elems("String 2", true);
        elist.add(two);
        Elems three = new Elems("String 3", true);
        elist.add(three);
        Elems four = new Elems("String 4", true);
        elist.add(four);

        ArrayList<Elems> retlist = eb.addAll(elist, 2);
        assertEquals(null, retlist.get(0)); // nothing got popped off when string 2 was added
        assertEquals(true, retlist.get(1) == one); // string 1 popped off when string 3 was added
        assertEquals(true, retlist.get(2) == four); // string 4 never got added

        ArrayList<Elems> retlist2 = eb.addAll(retlist, 3);
        assertEquals(null, retlist2.get(0)); // null events don't get added
        assertEquals(true, retlist2.get(1) == two); // when string 1 was added
        assertEquals(true, retlist2.get(2) == three); // when string 4 was added

        ArrayList<Elems> retlist3 = eb.addAll(retlist2, 4); // upto index is > list size
        assertEquals(retlist3, retlist2);

    }


    @Test
    public void testBufferCycles() {
        CircularBuffer<Elems> eb = new CircularBuffer<Elems>(100);
        ArrayList<Elems> elist = new ArrayList<Elems>();
        for (int i=0; i<100; i++) {
            elist.add(new Elems(""+i, true));
        }
        ArrayList<Elems> retlist = eb.addAll(elist, 100);
        for (int i=0; i<retlist.size(); i++) {
            assertTrue(retlist.get(i) == null);
        }
        elist.clear();
        for (int i=100; i<200; i++) {
            elist.add(new Elems(""+i, true));
        }
        retlist = eb.addAll(elist, 100);
        for (int i=0; i<retlist.size(); i++) {
            assertTrue(retlist.get(i).str.equals(""+i));
        }
        retlist = eb.addAll(retlist, 50);
        for (int i=0; i<retlist.size(); i++) {
            if (i<50) {
                assertTrue(retlist.get(i).str.equals(""+(i+100)));
            } else {
                assertTrue(retlist.get(i).str.equals(""+i));
            }
        }

        eb.clear();
        ArrayList<Elems> list1 = new ArrayList<Elems>();
        ArrayList<Elems> list2 = new ArrayList<Elems>();
        ArrayList<Elems> list3 = new ArrayList<Elems>();
        for (int i=0; i<1000; i++) {
            fillupLists(list1, list2, list3);
            eb.addAll(list1, list1.size());
            eb.addAll(list2, list2.size());
            eb.addAll(list3, list3.size());
        }
        int k = 999;
        for (Elems e : eb) { // reverse iterator
            assertTrue(e.str.equals(""+k));
            k--;
        }

    }

    private void fillupLists(ArrayList<Elems> list1, ArrayList<Elems> list2,
                            ArrayList<Elems> list3) {
        list1.clear();
        list2.clear();
        list3.clear();
        for (int i=0; i<100; i++) {
            list1.add(new Elems(""+(i+500), true));
            list2.add(new Elems(""+(i+700), true));
            list3.add(new Elems(""+(i+900), true));
        }
    }

}
