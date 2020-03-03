package com.example.testacm.java8;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TestStream {


    public static void main(String[] args) {


        List<String> list = new ArrayList<>();
        list.add("1");
        list.add("2");
        list.add("3");
        list.add("4");
        list.add("5");


        List<String> r = list.stream()
                .filter((e)->{
                    return Integer.parseInt(e) % 2 == 0;
                })
                .map((e)->{
                    return e + "map";
                })
                .collect(Collectors.toList());

        System.out.println(list);
        System.out.println(r);
    }

    static void testLambda() {

        IA ia = (int a, int b) -> {
            return a + b;
        };

        IB ib = ()-> 3;

        IC ic = x->x;

        Function<Integer, Integer> f = (a)->{
            return a * 3;
        };

//        f(3);

//        Function

//        Function<Integer, Integer> f = Function {
//
//        };
    }
}


interface IA {
    int fun(int a, int b);


}

interface IB {
    int run();
}

interface IC {
    int run(int a);
}
