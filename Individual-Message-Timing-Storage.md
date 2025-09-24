# Individual Message Timing Storage Implementation

## Overview
**Purpose**: Store individual end-to-end timing data for each message ID with dictionary classification to enable detailed performance analysis.

**Problem**: Currently, individual timing data is calculated correctly but immediately aggregated and discarded, losing valuable per-message insights.

**Solution**: Persistent storage of completed message timings with memory management and rich query capabilities.

## Architecture Design

### Data Structure

#### CompletedMessageTiming Class
```java
/**
 * Individual message timing record with dictionary classification
 */
private static class CompletedMessageTiming {
    final String messageId;                 // Object ID (e.g., "123")
    final long endToEndLatencyMs;           // Complete server→client timing
    final long serverLatencyMs;             // Server processing only
    final boolean usedDictionary;           // Dictionary compression flag
    final long completionTimestamp;         // When measurement completed
    final long networkClientLatencyMs;      // Network + Client processing time
    
    CompletedMessageTiming(MessageLatencyData data) {
        this.messageId = data.messageId;
        this.endToEndLatencyMs = data.endToEndLatencyMs;
        this.serverLatencyMs = data.serverLatencyNanos != null ? data.serverLatencyNanos / 1_000_000 : 0;
        this.usedDictionary = data.usedDictionary;
        this.completionTimestamp = System.currentTimeMillis();
        this.networkClientLatencyMs = this.endToEndLatencyMs - this.serverLatencyMs;
    }
    
    @Override
    public String toString() {
        return String.format("ID=%s, E2E=%dms, Server=%dms, Network=%dms, Dict=%s", 
            messageId, endToEndLatencyMs, serverLatencyMs, networkClientLatencyMs, usedDictionary);
    }
}
```

#### Storage Fields
```java
// ========== INDIVIDUAL TIMING STORAGE ==========
// Store individual timing data per message ID with dictionary classification
private final ConcurrentHashMap<String, CompletedMessageTiming> completedTimings = 
        ENABLE_MESSAGE_CORRELATION ? new ConcurrentHashMap<>() : null;

// Configuration for individual timing storage
private static final int MAX_COMPLETED_TIMINGS = 10000;        // Memory limit
private static final long TIMING_RETENTION_MS = 300000;        // 5 minutes retention
```

### Memory Management

#### Cleanup Methods
```java
/**
 * Cleanup expired and excessive timing records
 */
private void cleanupCompletedTimings() {
    if (!ENABLE_MESSAGE_CORRELATION || completedTimings == null) return;
    
    // Size-based cleanup
    if (completedTimings.size() > MAX_COMPLETED_TIMINGS) {
        cleanupOldestTimings();
    }
    
    // Time-based cleanup  
    cleanupExpiredTimings();
}

/**
 * Remove oldest 20% when size limit exceeded
 */
private void cleanupOldestTimings() {
    int targetSize = (int)(MAX_COMPLETED_TIMINGS * 0.8);
    
    completedTimings.entrySet().stream()
        .sorted(Comparator.comparing(e -> e.getValue().completionTimestamp))
        .limit(completedTimings.size() - targetSize)
        .forEach(entry -> completedTimings.remove(entry.getKey()));
        
    log.debug("Cleaned up {} oldest timing records, remaining: {}", 
        completedTimings.size() - targetSize, completedTimings.size());
}

/**
 * Remove timing records older than retention period
 */
private void cleanupExpiredTimings() {
    long cutoff = System.currentTimeMillis() - TIMING_RETENTION_MS;
    int initialSize = completedTimings.size();
    
    completedTimings.entrySet().removeIf(entry -> 
        entry.getValue().completionTimestamp < cutoff);
        
    int removedCount = initialSize - completedTimings.size();
    if (removedCount > 0) {
        log.debug("Cleaned up {} expired timing records", removedCount);
    }
}
```

### Integration with Existing Correlation System

#### Modified updateCorrelationStats Method
```java
/**
 * Update statistics when correlation is complete AND store individual timing
 */
private void updateCorrelationStats(MessageLatencyData data) {
    double serverMs = data.serverLatencyNanos != null ? data.serverLatencyNanos / 1_000_000.0 : 0;
    double e2eMs = data.endToEndLatencyMs;
    
    // ========== EXISTING AGGREGATION (UNCHANGED) ==========
    // Update dictionary-specific stats
    if (data.usedDictionary) {
        dictionaryEndToEndCount.incrementAndGet();
        totalDictionaryEndToEndMs.addAndGet(data.endToEndLatencyMs);
    } else {
        noDictionaryEndToEndCount.incrementAndGet();
        totalNoDictionaryEndToEndMs.addAndGet(data.endToEndLatencyMs);
    }
    
    // Log significant latencies
    if (e2eMs > 50) {
        log.info("Msg {} (dict={}): Server={}ms, E2E={}ms, Net+Client={}ms",
            data.messageId, data.usedDictionary, 
            String.format("%.1f", serverMs), 
            String.format("%.1f", e2eMs), 
            String.format("%.1f", e2eMs - serverMs));
    }
    
    // ========== NEW: STORE INDIVIDUAL TIMING ==========
    if (ENABLE_MESSAGE_CORRELATION && completedTimings != null) {
        CompletedMessageTiming timing = new CompletedMessageTiming(data);
        completedTimings.put(data.messageId, timing);
        
        // Periodic cleanup to prevent memory leaks
        if (completedTimings.size() % 100 == 0) {
            cleanupCompletedTimings();
        }
    }
}
```

### Query and Analysis Methods

#### Basic Access Methods
```java
/**
 * Get all completed message timings (with cleanup)
 */
public Map<String, CompletedMessageTiming> getCompletedTimings() {
    if (!ENABLE_MESSAGE_CORRELATION || completedTimings == null) {
        return Collections.emptyMap();
    }
    cleanupCompletedTimings(); // Cleanup expired entries
    return new HashMap<>(completedTimings);
}

/**
 * Get timing for specific message ID
 */
public CompletedMessageTiming getTimingById(String messageId) {
    if (!ENABLE_MESSAGE_CORRELATION || completedTimings == null) {
        return null;
    }
    return completedTimings.get(messageId);
}

/**
 * Get count of stored individual timings
 */
public int getStoredTimingCount() {
    if (!ENABLE_MESSAGE_CORRELATION || completedTimings == null) {
        return 0;
    }
    return completedTimings.size();
}
```

#### Dictionary-Specific Analysis
```java
/**
 * Get all messages that used dictionary compression
 */
public List<CompletedMessageTiming> getDictionaryTimings() {
    if (!ENABLE_MESSAGE_CORRELATION || completedTimings == null) {
        return Collections.emptyList();
    }
    return completedTimings.values().stream()
        .filter(t -> t.usedDictionary)
        .sorted(Comparator.comparing(t -> t.completionTimestamp))
        .collect(Collectors.toList());
}

/**
 * Get all messages that did NOT use dictionary compression
 */
public List<CompletedMessageTiming> getNonDictionaryTimings() {
    if (!ENABLE_MESSAGE_CORRELATION || completedTimings == null) {
        return Collections.emptyList();
    }
    return completedTimings.values().stream()
        .filter(t -> !t.usedDictionary)
        .sorted(Comparator.comparing(t -> t.completionTimestamp))
        .collect(Collectors.toList());
}

/**
 * Compare average latency between dictionary and non-dictionary messages
 */
public double getAverageLatencyByDictionary(boolean usedDictionary) {
    if (!ENABLE_MESSAGE_CORRELATION || completedTimings == null) {
        return 0.0;
    }
    return completedTimings.values().stream()
        .filter(t -> t.usedDictionary == usedDictionary)
        .mapToLong(t -> t.endToEndLatencyMs)
        .average()
        .orElse(0.0);
}
```

#### Performance Analysis Methods
```java
/**
 * Find fastest and slowest messages
 */
public CompletedMessageTiming getFastestMessage() {
    if (!ENABLE_MESSAGE_CORRELATION || completedTimings == null) {
        return null;
    }
    return completedTimings.values().stream()
        .min(Comparator.comparing(t -> t.endToEndLatencyMs))
        .orElse(null);
}

public CompletedMessageTiming getSlowestMessage() {
    if (!ENABLE_MESSAGE_CORRELATION || completedTimings == null) {
        return null;
    }
    return completedTimings.values().stream()
        .max(Comparator.comparing(t -> t.endToEndLatencyMs))
        .orElse(null);
}

/**
 * Find messages above performance threshold
 */
public List<CompletedMessageTiming> getSlowMessages(long thresholdMs) {
    if (!ENABLE_MESSAGE_CORRELATION || completedTimings == null) {
        return Collections.emptyList();
    }
    return completedTimings.values().stream()
        .filter(t -> t.endToEndLatencyMs > thresholdMs)
        .sorted((a, b) -> Long.compare(b.endToEndLatencyMs, a.endToEndLatencyMs))
        .collect(Collectors.toList());
}

/**
 * Get timing percentiles for detailed analysis
 */
public double getTimingPercentile(double percentile, boolean dictionaryOnly) {
    if (!ENABLE_MESSAGE_CORRELATION || completedTimings == null) {
        return 0.0;
    }
    
    List<Long> timings = completedTimings.values().stream()
        .filter(t -> !dictionaryOnly || t.usedDictionary)
        .map(t -> t.endToEndLatencyMs)
        .sorted()
        .collect(Collectors.toList());
        
    if (timings.isEmpty()) return 0.0;
    
    int index = (int)(percentile * (timings.size() - 1) / 100.0);
    return timings.get(index);
}
```

#### Enhanced Reporting Methods
```java
/**
 * Generate detailed individual timing report
 */
public String generateIndividualTimingReport() {
    if (!ENABLE_MESSAGE_CORRELATION || completedTimings == null || completedTimings.isEmpty()) {
        return "Individual timing tracking disabled or no data available";
    }
    
    cleanupCompletedTimings();
    
    StringBuilder report = new StringBuilder();
    report.append("=== INDIVIDUAL MESSAGE TIMING ANALYSIS ===\n");
    report.append(String.format("Stored Timings: %d (max: %d)\n", 
        completedTimings.size(), MAX_COMPLETED_TIMINGS));
    
    // Dictionary vs Non-Dictionary breakdown
    List<CompletedMessageTiming> dictTimings = getDictionaryTimings();
    List<CompletedMessageTiming> nonDictTimings = getNonDictionaryTimings();
    
    report.append(String.format("Dictionary Messages: %d (avg: %.1fms)\n", 
        dictTimings.size(), getAverageLatencyByDictionary(true)));
    report.append(String.format("Non-Dictionary Messages: %d (avg: %.1fms)\n", 
        nonDictTimings.size(), getAverageLatencyByDictionary(false)));
    
    // Performance extremes
    CompletedMessageTiming fastest = getFastestMessage();
    CompletedMessageTiming slowest = getSlowestMessage();
    
    if (fastest != null) {
        report.append(String.format("Fastest: %s\n", fastest));
    }
    if (slowest != null) {
        report.append(String.format("Slowest: %s\n", slowest));
    }
    
    // Slow message analysis
    List<CompletedMessageTiming> slowMessages = getSlowMessages(100); // > 100ms
    if (!slowMessages.isEmpty()) {
        report.append(String.format("\nSlow Messages (>100ms): %d\n", slowMessages.size()));
        slowMessages.stream()
            .limit(5) // Top 5 slowest
            .forEach(t -> report.append(String.format("  %s\n", t)));
    }
    
    return report.toString();
}
```

## Usage Examples

### Basic Usage
```java
// Get individual timing for specific message
CompletedMessageTiming timing = latencyTracker.getTimingById("123");
if (timing != null) {
    System.out.printf("Message 123: %dms end-to-end, dictionary=%s%n", 
        timing.endToEndLatencyMs, timing.usedDictionary);
}

// Get all dictionary-compressed message timings
List<CompletedMessageTiming> dictMessages = latencyTracker.getDictionaryTimings();
System.out.printf("Found %d dictionary-compressed messages%n", dictMessages.size());
```

### Performance Analysis
```java
// Compare dictionary vs non-dictionary performance
double dictAvg = latencyTracker.getAverageLatencyByDictionary(true);
double nonDictAvg = latencyTracker.getAverageLatencyByDictionary(false);
System.out.printf("Dictionary: %.1fms avg, Non-dictionary: %.1fms avg%n", dictAvg, nonDictAvg);

// Find performance outliers
List<CompletedMessageTiming> slowMessages = latencyTracker.getSlowMessages(100);
System.out.printf("Found %d messages slower than 100ms%n", slowMessages.size());

// Generate detailed report
String report = latencyTracker.generateIndividualTimingReport();
System.out.println(report);
```

## Memory and Performance Characteristics

### Memory Usage
- **Per Message**: ~80 bytes (CompletedMessageTiming object)
- **Typical Load**: 1000 messages = ~80KB
- **Maximum Load**: 10000 messages = ~800KB (with automatic cleanup)

### Performance Impact
- **Storage**: O(1) HashMap insertion
- **Cleanup**: O(n) periodic cleanup (every 100 messages)
- **Queries**: O(n) for filtering operations, O(1) for direct lookup
- **Thread Safety**: ConcurrentHashMap ensures thread-safe operations

### Configuration
```java
private static final int MAX_COMPLETED_TIMINGS = 10000;        // Adjust based on memory requirements
private static final long TIMING_RETENTION_MS = 300000;        // 5 minutes (adjust based on analysis needs)
```

## Implementation Plan

### Phase 1: Core Data Structure
1. Add CompletedMessageTiming class definition
2. Add storage fields and configuration constants
3. Add memory management methods

### Phase 2: Integration
1. Modify updateCorrelationStats() to store individual timings
2. Add cleanup trigger in correlation completion
3. Test memory management under load

### Phase 3: Query Methods
1. Implement basic access methods
2. Add dictionary-specific analysis methods  
3. Add performance analysis methods

### Phase 4: Enhanced Reporting
1. Add detailed reporting methods
2. Add percentile calculations
3. Add comprehensive analysis report generation

## Benefits

### Performance Insights
- **Per-Message Analysis**: Identify which specific objects are slow
- **Dictionary Effectiveness**: Compare actual performance impact per message
- **Performance Regression Detection**: Track individual message performance over time

### Debugging Capabilities
- **Slow Message Investigation**: Find exact messages causing performance issues
- **Dictionary Optimization**: Identify which message types benefit most from compression
- **Network vs Server Analysis**: Separate server processing from network/client delays

### Production Monitoring
- **Real-time Performance Tracking**: Monitor individual message performance
- **Automated Alerting**: Detect performance degradation in specific message types
- **Capacity Planning**: Understand message performance distribution

This implementation provides comprehensive individual message timing storage while maintaining memory safety and performance characteristics suitable for production use.

## 🚨 DECEMBER 2024 UPDATE: Dictionary Timing Consistency

### Integration with Dictionary Performance Comparison

The individual message timing storage system provides the foundation for **consistent dictionary performance comparison** using true end-to-end timing.

**Key Integration Points:**

#### 1. Unified Timing Methodology
```java
// Both systems now use the same end-to-end timing source
CompletedMessageTiming timing = new CompletedMessageTiming(data);
// timing.endToEndLatencyMs = server start → client DOM + ACK
// timing.usedDictionary = dictionary classification from message send
```

#### 2. Dictionary Performance Analysis
```java
// Get true end-to-end dictionary comparison
double dictAvg = latencyTracker.getAverageLatencyByDictionary(true);    // Dictionary messages
double nonDictAvg = latencyTracker.getAverageLatencyByDictionary(false); // Non-dictionary messages

// Result: Both measure server start → client DOM completion + ACK
// Example: Dictionary: 340ms avg, Non-dictionary: 350ms avg
```

#### 3. Replacement of Network-Only Dictionary Metrics

**Before**: Dictionary comparison used frame write → network ACK timing (1ms range)
**Now**: Dictionary comparison uses correlation → client DOM + ACK timing (347ms range)

**Benefits:**
- ✅ **Consistent Methodology**: Same timing endpoints for fair comparison
- ✅ **User-Perceived Performance**: Measures actual UI responsiveness impact
- ✅ **Individual Message Insights**: Can analyze specific slow dictionary vs non-dictionary messages
- ✅ **Production Monitoring**: Real-time dictionary effectiveness tracking

#### 4. Enhanced Dictionary Analysis Methods

The individual timing storage enables detailed dictionary performance analysis:

```java
// Find slow dictionary messages specifically
List<CompletedMessageTiming> slowDictMessages = latencyTracker.getDictionaryTimings()
    .stream()
    .filter(t -> t.endToEndLatencyMs > 500) // > 500ms
    .collect(Collectors.toList());

// Compare dictionary vs non-dictionary percentiles
double dictP95 = latencyTracker.getTimingPercentile(95, true);   // Dictionary P95
double nonDictP95 = latencyTracker.getTimingPercentile(95, false); // Non-dictionary P95
```

This integration ensures dictionary performance metrics accurately reflect user experience rather than just network layer performance.