#!/usr/bin/env node

/**
 * VerveguardAPI Stress Test
 *
 * Stress tests the transfer endpoint using multiple merchants.
 * Each iteration logs in as a different merchant and transfers from their account.
 *
 * Usage:
 *   node stress-test.js [options]
 *
 * Options:
 *   --iterations, -n    Number of transfers to execute (default: 50, max: 200)
 *   --concurrency, -c   Number of concurrent requests (default: 10)
 *   --base-url, -u      Base URL (default: http://localhost:8080/api/v1)
 *   --verbose, -v       Enable verbose logging (shows each request details)
 *   --help, -h          Show help
 *
 * Examples:
 *   node stress-test.js                  # 50 transfers, 10 concurrent
 *   node stress-test.js -n 100 -c 20     # 100 transfers, 20 concurrent
 *   node stress-test.js -n 200 -c 50     # Max load test (200 merchants)
 *   node stress-test.js -n 5 -v          # Debug mode with verbose logging
 */

const args = process.argv.slice(2);
const config = {
    iterations: 50,
    concurrency: 10,
    baseUrl: 'http://localhost:8080/api/v1',
    password: 'Admin123!',
    maxMerchants: 200
};

for (let i = 0; i < args.length; i++) {
    switch (args[i]) {
        case '--iterations':
        case '-n':
            config.iterations = Math.min(parseInt(args[++i]), config.maxMerchants);
            break;
        case '--concurrency':
        case '-c':
            config.concurrency = parseInt(args[++i]);
            break;
        case '--base-url':
        case '-u':
            config.baseUrl = args[++i];
            break;
        case '--help':
        case '-h':
            console.log(`
VerveguardAPI Stress Test

Uses ${config.maxMerchants} pre-seeded merchants (merchant1@stresstest.com to merchant${config.maxMerchants}@stresstest.com).
Each iteration logs in as a different merchant and transfers from their account.

Usage: node stress-test.js [options]

Options:
  --iterations, -n    Number of transfers (default: 50, max: ${config.maxMerchants})
  --concurrency, -c   Concurrent requests (default: 10)
  --base-url, -u      Base URL (default: http://localhost:8080/api/v1)
  --verbose, -v       Enable verbose logging (shows each request details)
  --help, -h          Show help

Examples:
  node stress-test.js                  # 50 transfers, 10 concurrent
  node stress-test.js -n 100 -c 20     # 100 transfers, 20 concurrent
  node stress-test.js -n 200 -c 50     # Max load test
  node stress-test.js -n 5 -v          # Debug mode with verbose logging
`);
            process.exit(0);
    }
}

// Statistics
const stats = {
    total: 0,
    success: 0,
    failed: 0,
    responseTimes: [],  // transfer only
    loginTimes: [],     // login only
    errors: [],
    startTime: null,
    endTime: null
};

function generateUUID() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
        const r = Math.random() * 16 | 0;
        const v = c === 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}

function randomAmount() {
    return Math.floor(Math.random() * 4900) + 100;
}

function formatMs(ms) {
    return `${ms.toFixed(2)}ms`;
}

function percentile(arr, p) {
    if (arr.length === 0) return 0;
    const sorted = [...arr].sort((a, b) => a - b);
    const index = Math.ceil((p / 100) * sorted.length) - 1;
    return sorted[Math.max(0, index)];
}

function getMerchantCredentials(merchantIndex) {
    const fromPadded = String(merchantIndex).padStart(8, '0');
    const toPadded = String(merchantIndex + 200).padStart(8, '0');
    return {
        email: `merchant${merchantIndex}@stresstest.com`,
        fromAccount: `33${fromPadded}`,
        toAccount: `33${toPadded}`,
        cardNumber: `4${String(merchantIndex).padStart(15, '0')}`
    };
}

const VERBOSE = process.argv.includes('--verbose') || process.argv.includes('-v');

function log(msg) {
    if (VERBOSE) console.log(msg);
}

async function login(email) {
    const response = await fetch(`${config.baseUrl}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            email: email,
            password: config.password
        })
    });

    if (!response.ok) {
        const text = await response.text();
        throw new Error(`Login failed for ${email}: ${response.status} - ${text}`);
    }

    const data = await response.json();
    return data.data.accessToken;
}

async function executeTransfer(merchantIndex) {
    const totalStart = performance.now();
    const { email, fromAccount, toAccount, cardNumber } = getMerchantCredentials(merchantIndex);
    const idempotencyKey = `${generateUUID()}-${Date.now()}-${merchantIndex}`;
    const amount = randomAmount();

    log(`\n[#${merchantIndex}] Starting...`);
    log(`[#${merchantIndex}] Email: ${email}`);
    log(`[#${merchantIndex}] Card Number: ${cardNumber}`);
    log(`[#${merchantIndex}] From: ${fromAccount} → To: ${toAccount}`);
    log(`[#${merchantIndex}] Amount: ${amount} NGN`);

    try {
        log(`[#${merchantIndex}] Logging in...`);
        const loginStart = performance.now();
        const accessToken = await login(email);
        const loginTime = performance.now() - loginStart;
        log(`[#${merchantIndex}] Login OK in ${formatMs(loginTime)}, token: ${accessToken.substring(0, 20)}...`);

        const transferBody = {
            fromAccountNumber: fromAccount,
            toAccountNumber: toAccount,
            amount: amount,
            currency: 'NGN',
            cardNumber: cardNumber,
            description: `Stress test transfer #${merchantIndex}`
        };
        log(`[#${merchantIndex}] Transfer request: ${JSON.stringify(transferBody)}`);

        const transferStart = performance.now();
        const response = await fetch(`${config.baseUrl}/transfers/me`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${accessToken}`,
                'X-Idempotency-Key': idempotencyKey
            },
            body: JSON.stringify(transferBody)
        });
        const transferTime = performance.now() - transferStart;
        const totalTime = performance.now() - totalStart;

        stats.responseTimes.push(transferTime);
        stats.loginTimes.push(loginTime);
        stats.total++;

        if (response.status === 201) {
            stats.success++;
            const data = await response.json();
            log(`[#${merchantIndex}] SUCCESS - ID: ${data.data?.id}`);
            process.stdout.write(`\r✅ #${merchantIndex} (${email}) login=${formatMs(loginTime)} transfer=${formatMs(transferTime)} total=${formatMs(totalTime)} ID:${data.data?.id || 'N/A'}          `);
        } else {
            stats.failed++;
            let errorMsg = '';
            try {
                const errorData = await response.json();
                errorMsg = errorData.errorMessage || errorData.message || JSON.stringify(errorData);
            } catch {
                errorMsg = await response.text();
            }
            log(`[#${merchantIndex}] FAILED - ${response.status}: ${errorMsg}`);
            stats.errors.push({ index: merchantIndex, email, fromAccount, toAccount, status: response.status, message: errorMsg });
            process.stdout.write(`\r❌ #${merchantIndex} (${email}) failed: ${response.status} transfer=${formatMs(transferTime)} total=${formatMs(totalTime)}          `);
        }

        return { success: response.status === 201, transferTime, loginTime, totalTime };

    } catch (error) {
        const totalTime = performance.now() - totalStart;
        stats.responseTimes.push(totalTime);
        stats.total++;
        stats.failed++;
        log(`[#${merchantIndex}] ERROR - ${error.message}`);
        stats.errors.push({ index: merchantIndex, email, fromAccount, toAccount, status: 'ERROR', message: error.message });
        process.stdout.write(`\r❌ #${merchantIndex} error: ${error.message.substring(0, 50)}          `);
        return { success: false, totalTime, error };
    }
}

async function runWithConcurrency(total, concurrency) {
    const results = [];
    let currentIndex = 0;

    async function runNext() {
        if (currentIndex >= total) return;
        const index = ++currentIndex;
        const result = await executeTransfer(index);
        results.push(result);
        await runNext();
    }

    const workers = [];
    for (let i = 0; i < Math.min(concurrency, total); i++) {
        workers.push(runNext());
    }

    await Promise.all(workers);
    return results;
}

function printSummary() {
    const duration = stats.endTime - stats.startTime;
    const throughput = stats.total / (duration / 1000);
    const successRate = stats.total > 0 ? (stats.success / stats.total * 100) : 0;

    // Transfer metrics
    const avgTransfer = stats.responseTimes.length > 0
        ? stats.responseTimes.reduce((a, b) => a + b, 0) / stats.responseTimes.length : 0;
    const minTransfer = stats.responseTimes.length > 0 ? Math.min(...stats.responseTimes) : 0;
    const maxTransfer = stats.responseTimes.length > 0 ? Math.max(...stats.responseTimes) : 0;
    const p50 = percentile(stats.responseTimes, 50);
    const p95 = percentile(stats.responseTimes, 95);
    const p99 = percentile(stats.responseTimes, 99);

    // Login metrics
    const avgLogin = stats.loginTimes.length > 0
        ? stats.loginTimes.reduce((a, b) => a + b, 0) / stats.loginTimes.length : 0;
    const minLogin = stats.loginTimes.length > 0 ? Math.min(...stats.loginTimes) : 0;
    const maxLogin = stats.loginTimes.length > 0 ? Math.max(...stats.loginTimes) : 0;

    console.log('\n');
    console.log('═══════════════════════════════════════════════════════════');
    console.log('                    STRESS TEST SUMMARY                     ');
    console.log('═══════════════════════════════════════════════════════════');
    console.log('');
    console.log('  Configuration:');
    console.log(`    Base URL:        ${config.baseUrl}`);
    console.log(`    Target:          POST /transfers/me`);
    console.log(`    Iterations:      ${config.iterations}`);
    console.log(`    Concurrency:     ${config.concurrency}`);
    console.log(`    Merchants Used:  merchant1 to merchant${config.iterations}`);
    console.log('');
    console.log('  Results:');
    console.log(`    Total Requests:  ${stats.total}`);
    console.log(`    Successful:      ${stats.success} ✅`);
    console.log(`    Failed:          ${stats.failed} ❌`);
    console.log(`    Success Rate:    ${successRate.toFixed(2)}%`);
    console.log('');
    console.log('  Overall:');
    console.log(`    Total Duration:  ${(duration / 1000).toFixed(2)}s`);
    console.log(`    Throughput:      ${throughput.toFixed(2)} req/s`);
    console.log('');
    console.log('  Transfer Performance (excl. login):');
    console.log(`    Avg:             ${formatMs(avgTransfer)}`);
    console.log(`    Min:             ${formatMs(minTransfer)}`);
    console.log(`    Max:             ${formatMs(maxTransfer)}`);
    console.log(`    P50 (Median):    ${formatMs(p50)}`);
    console.log(`    P95:             ${formatMs(p95)}`);
    console.log(`    P99:             ${formatMs(p99)}`);
    console.log('');
    console.log('  Login Performance:');
    console.log(`    Avg:             ${formatMs(avgLogin)}`);
    console.log(`    Min:             ${formatMs(minLogin)}`);
    console.log(`    Max:             ${formatMs(maxLogin)}`);
    console.log('');

    if (stats.errors.length > 0) {
        const showCount = Math.min(stats.errors.length, 10);
        console.log(`  Errors: ${stats.errors.length} total (showing first ${showCount})`);
        stats.errors.slice(0, showCount).forEach(err => {
            console.log(`    #${err.index} (${err.email})`);
            console.log(`      From: ${err.fromAccount} → To: ${err.toAccount}`);
            console.log(`      ${err.status}: ${err.message.substring(0, 60)}`);
        });
        console.log('');
    }

    console.log('═══════════════════════════════════════════════════════════');
}

async function main() {
    console.log('═══════════════════════════════════════════════════════════');
    console.log('           VerveguardAPI Stress Test Runner                ');
    console.log('═══════════════════════════════════════════════════════════');
    console.log('');
    console.log(`  Iterations:    ${config.iterations}`);
    console.log(`  Concurrency:   ${config.concurrency}`);
    console.log(`  Base URL:      ${config.baseUrl}`);
    console.log(`  Merchants:     merchant1@stresstest.com to merchant${config.iterations}@stresstest.com`);
    console.log(`  Accounts:      3300000001-${String(config.iterations).padStart(8, '0')} → 33000002XX`);

    console.log('\n🚀 Starting stress test...\n');
    stats.startTime = performance.now();

    await runWithConcurrency(config.iterations, config.concurrency);

    stats.endTime = performance.now();

    printSummary();

    process.exit(stats.failed > 0 ? 1 : 0);
}

main().catch(err => {
    console.error('\n❌ Stress test failed:', err.message);
    process.exit(1);
});