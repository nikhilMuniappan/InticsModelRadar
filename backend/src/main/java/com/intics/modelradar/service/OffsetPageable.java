package com.intics.modelradar.service;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * A {@link Pageable} defined by an absolute offset + limit rather than a page index.
 *
 * <p>Vaadin's lazy grid asks its data provider for an arbitrary {@code (offset, limit)}
 * window, which doesn't always line up with Spring Data's page-number model. This
 * adapter feeds the offset straight through to the SQL {@code OFFSET/LIMIT}, so any
 * window works regardless of alignment.
 */
final class OffsetPageable implements Pageable {

    private final int offset;
    private final int limit;
    private final Sort sort;

    OffsetPageable(int offset, int limit, Sort sort) {
        this.offset = Math.max(0, offset);
        this.limit = Math.max(1, limit);
        this.sort = sort == null ? Sort.unsorted() : sort;
    }

    @Override
    public int getPageNumber() {
        return offset / limit;
    }

    @Override
    public int getPageSize() {
        return limit;
    }

    @Override
    public long getOffset() {
        return offset;
    }

    @Override
    public Sort getSort() {
        return sort;
    }

    @Override
    public Pageable next() {
        return new OffsetPageable(offset + limit, limit, sort);
    }

    @Override
    public Pageable previousOrFirst() {
        return hasPrevious() ? new OffsetPageable(offset - limit, limit, sort) : first();
    }

    @Override
    public Pageable first() {
        return new OffsetPageable(0, limit, sort);
    }

    @Override
    public Pageable withPage(int pageNumber) {
        return new OffsetPageable(pageNumber * limit, limit, sort);
    }

    @Override
    public boolean hasPrevious() {
        return offset >= limit;
    }
}
