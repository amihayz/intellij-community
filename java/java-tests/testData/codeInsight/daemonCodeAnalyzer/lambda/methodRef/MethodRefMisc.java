class MyTest {

    interface I {
        abstract void m1(int i);
    }

    static class A {
        void m(int i) {}
    }

    static class B extends A {
        void m(int i) {
            I mh = super::m;
            mh.m1(i);
        }
    }

    public static void main(String[] args) {
        new B().m(10);
    }
}

class MyTest1 {

    interface I {
        void m();
    }

    void call(I s) {}

    I i = <error descr="Cannot resolve symbol 'NonExistentType'">NonExistentType</error>::m;

    {
        call(<error descr="Cannot resolve symbol 'NonExistentType'">NonExistentType</error>::m);
    }
}