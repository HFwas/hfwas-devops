package com.hfwas.devops.limit;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * @author houfei
 * @package com.hfwas.devops.tools.limit
 * The public rate limit (without an API key) is 5 requests in a rolling 30 second window;
 * the rate limit with an API key is 50 requests in a rolling 30 second window
 * @date 2025/2/27
 */
@Slf4j
public class ApiLimitRate {
    private final BlockingQueue<Ticket> queue = new DelayQueue<>();
    private int quantity = 5;
    private long durationMilliseconds = 30 * 1000;

    public ApiLimitRate(int quantity, long durationMilliseconds) {
        this.quantity = quantity;
        this.durationMilliseconds = durationMilliseconds;
    }

    public synchronized Ticket getTicket() throws InterruptedException {
        if (queue.size() >= quantity) {
            Ticket ticket = queue.take();
            if (log.isDebugEnabled()) {
                final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
                final LocalTime time = LocalTime.now();
                log.debug("Ticket taken At: {}; count: {}", time.format(formatter), queue.size() + 1);
            }
            return ticket;
        }
        if (log.isDebugEnabled()) {
            final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            final LocalTime time = LocalTime.now();
            log.debug("Ticket taken At: {}; count: {}; by {}", time.format(formatter), queue.size() + 1,
                    Thread.currentThread().getId());
        }
        return new Ticket(this);
    }

    synchronized void replaceTicket() throws InterruptedException {
        if (queue.size() < quantity) {
            queue.put(new Ticket(this));
            if (log.isDebugEnabled()) {
                final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
                final LocalTime time = LocalTime.now();
                log.debug("Ticket returned At: {}; count: {}; by {}", time.format(formatter), queue.size() + 1,
                        Thread.currentThread().getId());
            }
        }
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public long getDurationMilliseconds() {
        return durationMilliseconds;
    }

    public void setDurationMilliseconds(long durationMilliseconds) {
        this.durationMilliseconds = durationMilliseconds;
    }

    public static class Ticket implements Delayed, AutoCloseable {
        private final ApiLimitRate meter;
        private final long startTime;

        Ticket(ApiLimitRate meter) {
            this.meter = meter;
            this.startTime = System.currentTimeMillis() + meter.getDurationMilliseconds();
        }

        @Override
        public long getDelay(TimeUnit unit) {
            long diff = startTime - System.currentTimeMillis();
            return unit.convert(diff, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return (int) (this.startTime - ((Ticket) o).startTime);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;
            Ticket ticket = (Ticket) o;
            return startTime == ticket.startTime;
        }

        @Override
        public int hashCode() {
            return Objects.hash(startTime);
        }

        @Override
        public void close() throws Exception {
            meter.replaceTicket();
        }
    }
}
