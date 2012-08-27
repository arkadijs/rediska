package ra

class Cleaner implements Runnable {

    static init() {
        def t = new Thread(new Cleaner())
        t.setDaemon(true)
        t.start()
    }

    def prevDay = -1

    @Override
    void run() {
        while (true) {
            try {
                maintain()
            } catch (Exception e) {
                e.printStackTrace()
            }
            Thread.sleep(60_000)
        }
    }

    void maintain() {
        def cal = Calendar.instance
        def day = cal.get(Calendar.DAY_OF_MONTH)
        if (prevDay != day) {
            def active = activeDatabases(cal)
            println "cleaner -> active databases = $active"
            // XXX synchronization
            RA.currentDatabase = day
            RA.activeDatabases = active
            def ra = new RA()
            (1..31).minus(active).each { i ->
                println "cleaner -> clearing database #$i"
                ra.resetDay(i)
            }
            ra.disconnect()
            prevDay = day
        }
    }

    List activeDatabases(Calendar cal) {
        def day = cal.get(Calendar.DAY_OF_MONTH)
        def oldestDatabaseIndex = day - RA.nKeepDays

        if (oldestDatabaseIndex >= 1)
            (oldestDatabaseIndex..day)
        else {
            cal.set(Calendar.DAY_OF_MONTH, 1)
            cal.add(Calendar.DAY_OF_MONTH, -1)
            def daysInPreviousMonth = cal.get(Calendar.DAY_OF_MONTH)
            [(daysInPreviousMonth + oldestDatabaseIndex)..daysInPreviousMonth, 1..day].flatten()
        }
    }

}
