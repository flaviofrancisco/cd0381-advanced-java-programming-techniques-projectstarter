package com.udacity.webcrawler;

import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Pattern;

public final class CrawlInternalTask extends RecursiveAction {

    private  final String url;
    private final Instant deadline;
    private final int maxDepth;
    private final ConcurrentHashMap<String, Integer> counts;
    private final ConcurrentSkipListSet<String> visitedUrls;
    private final PageParserFactory parserFactory;
    private final List<Pattern> ignoredUrls;


    public CrawlInternalTask(
            String url,
            Instant deadline,
            int maxDepth,
            ConcurrentHashMap<String, Integer> counts,
            ConcurrentSkipListSet<String> visitedUrls,
            PageParserFactory parserFactory,
            List<Pattern> ignoredUrls) {
        this.url = url;
        this.deadline = deadline;
        this.maxDepth = maxDepth;
        this.counts = counts;
        this.visitedUrls = visitedUrls;
        this.parserFactory = parserFactory;
        this.ignoredUrls = ignoredUrls;
    }
    @Override
    protected void compute() {

        if (maxDepth == 0 || Instant.now().isAfter(deadline)) {
            return;
        }

        for (Pattern pattern : ignoredUrls) {
            if (pattern.matcher(url).matches()) {
                return;
            }
        }

        synchronized (this) {
            if (visitedUrls.contains(url)) {
                return;
            }
            visitedUrls.add(url);
        }

        PageParser.Result result = parserFactory.get(url).parse();

        for (ConcurrentMap.Entry<String, Integer> e : result.getWordCounts().entrySet()) {
            counts.compute(e.getKey(), (k, v) -> v == null ? e.getValue() : v + e.getValue());
        }

        List<CrawlInternalTask> subTasks = new ArrayList<>();
        for (String link : result.getLinks()) {
            CrawlInternalTask task = new CrawlInternalTask(link, deadline, maxDepth - 1, counts, visitedUrls, parserFactory, ignoredUrls);
            subTasks.add(task);
        }
        invokeAll(subTasks);
    }
}
