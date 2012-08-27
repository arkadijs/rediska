package ra

class CleanerTest  extends GroovyTestCase {

    def cleaner = new Cleaner()

    void testActiveIndices() {
        // assume RA.nKeepDays == 2
        assert [29, 30,  1] == cleaner.activeDatabases(new GregorianCalendar(2012, 4, 1))
        assert [30,  1,  2] == cleaner.activeDatabases(new GregorianCalendar(2012, 4, 2))
        assert [ 1,  2,  3] == cleaner.activeDatabases(new GregorianCalendar(2012, 4, 3))
        assert [29, 30, 31] == cleaner.activeDatabases(new GregorianCalendar(2012, 0, 31))
    }

    void testMaintain() {
        cleaner.maintain()
    }
}
