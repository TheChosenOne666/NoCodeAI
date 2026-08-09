/**
 * 导入精品案例部署作品到 MySQL 的 app_deploy_asset 表。
 *
 * 仅导入以下 7 个精品案例目录（对应前端 fallbackFeaturedApps 的 deployKey）：
 *   H0wUnd, wLxkTw, r7BaUv, I00Oyc, jkJ12P, WVsVTS, rpkcN3
 *
 * 用法：在 tmp_import/ 目录执行  node import_featured_assets.js
 * 依赖：mysql2（npm i mysql2）
 */
const fs = require('fs');
const path = require('path');
const mysql = require('mysql2/promise');

// 数据库连接配置。
// 默认连本地库（与 application.yml 一致）；若设置了 DB_HOST 等环境变量则切换到目标库（如 Railway）。
// 用法：
//   本地：node import_featured_assets.js
//   线上（Railway Public Network）：
//     $env:DB_HOST="xxx.railway.app"; $env:DB_PORT="3306"; $env:DB_USER="root";
//     $env:DB_PASSWORD="xxxxx"; $env:DB_NAME="railway"; node import_featured_assets.js
const DB_CONFIG = {
    host: process.env.DB_HOST || 'localhost',
    port: Number(process.env.DB_PORT || 3306),
    user: process.env.DB_USER || 'root',
    password: process.env.DB_PASSWORD || '123456',
    database: process.env.DB_NAME || 'nocode',
    charset: 'utf8mb4',
};

// 仅导入这 7 个精品案例
const FEATURED_DEPLOY_KEYS = [
    'H0wUnd', 'wLxkTw', 'r7BaUv', 'I00Oyc', 'jkJ12P', 'WVsVTS', 'rpkcN3',
];

const CODE_DEPLOY_ROOT = path.resolve(__dirname, '..', 'tmp', 'code_deploy');

function getContentType(filePath) {
    const ext = path.extname(filePath).toLowerCase();
    const map = {
        '.html': 'text/html; charset=UTF-8',
        '.htm': 'text/html; charset=UTF-8',
        '.css': 'text/css; charset=UTF-8',
        '.js': 'application/javascript; charset=UTF-8',
        '.mjs': 'application/javascript; charset=UTF-8',
        '.json': 'application/json; charset=UTF-8',
        '.png': 'image/png',
        '.jpg': 'image/jpeg',
        '.jpeg': 'image/jpeg',
        '.gif': 'image/gif',
        '.svg': 'image/svg+xml',
        '.webp': 'image/webp',
        '.ico': 'image/x-icon',
        '.woff': 'font/woff',
        '.woff2': 'font/woff2',
        '.ttf': 'font/ttf',
        '.txt': 'text/plain; charset=UTF-8',
        '.map': 'application/json; charset=UTF-8',
    };
    return map[ext] || 'application/octet-stream';
}

/** 递归收集目录下所有文件，返回相对路径（用 / 分隔）列表 */
function collectFiles(dir, baseDir, acc) {
    acc = acc || [];
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
        const full = path.join(dir, entry.name);
        if (entry.isDirectory()) {
            collectFiles(full, baseDir, acc);
        } else if (entry.isFile()) {
            const rel = path.relative(baseDir, full).split(path.sep).join('/');
            acc.push({ full, rel });
        }
    }
    return acc;
}

async function main() {
    const conn = await mysql.createConnection(DB_CONFIG);
    // 幂等建表
    const ddl = fs.readFileSync(path.resolve(__dirname, '..', 'src', 'main', 'resources', 'sql', 'app_deploy_asset.sql'), 'utf8');
    await conn.query(ddl);
    console.log('[建表] app_deploy_asset 已就绪（如已存在则跳过）');
    let totalInserted = 0;
    try {
        for (const deployKey of FEATURED_DEPLOY_KEYS) {
            const dir = path.join(CODE_DEPLOY_ROOT, deployKey);
            if (!fs.existsSync(dir)) {
                console.warn(`[跳过] 目录不存在: ${dir}`);
                continue;
            }
            const files = collectFiles(dir, dir);
            if (files.length === 0) {
                console.warn(`[跳过] 目录为空: ${deployKey}`);
                continue;
            }
            // 先清空该 deployKey 旧数据，保证幂等
            await conn.execute('DELETE FROM app_deploy_asset WHERE deploy_key = ?', [deployKey]);
            for (const { full, rel } of files) {
                const content = fs.readFileSync(full);
                const contentType = getContentType(rel);
                await conn.execute(
                    'INSERT INTO app_deploy_asset (deploy_key, file_path, content_type, file_size, content, is_delete) '
                    + 'VALUES (?, ?, ?, ?, ?, 0)',
                    [deployKey, rel, contentType, content.length, content]
                );
                totalInserted++;
                console.log(`  导入 ${deployKey}/${rel} (${(content.length / 1024).toFixed(1)} KB)`);
            }
            console.log(`[完成] ${deployKey}: ${files.length} 个文件`);
        }
        console.log(`\n全部导入完成，共写入 ${totalInserted} 个文件。`);
    } finally {
        await conn.end();
    }
}

main().catch((e) => {
    console.error('导入失败:', e);
    process.exit(1);
});
