#!/usr/bin/env node
const fs = require("fs");
const path = require("path");

const base = (
  process.env.WINE_SERVER_URL || "https://eiter.freeboxos.fr/wine"
).trim().replace(/\/$/, "") + "/";

const swift = `// Généré au build CI — NE PAS ÉDITER
import Foundation

enum BuildConfig {
    static let apiBaseString = ${JSON.stringify(base)}
    static let apiFallbacks: [String] = []
    static var apiBase: URL { URL(string: apiBaseString)! }
}
`;

fs.writeFileSync(
  path.join(__dirname, "..", "native-ios", "Config", "Build.xcconfig"),
  `WINE_API_BASE = ${base}\n`,
);
fs.writeFileSync(
  path.join(__dirname, "..", "native-ios", "WineNative", "Sources", "BuildConfig.generated.swift"),
  swift,
);
console.log(`API : ${base}`);