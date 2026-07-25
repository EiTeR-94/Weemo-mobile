import UIKit

/// Empreinte visuelle étiquette (dHash + aHash), crop centre — miroir de
/// app/static/js/label-cache.js côté serveur (mêmes constantes : crop 78 %,
/// grille dHash 9x8, grille aHash 8x8, luma via conversion niveaux de gris).
enum LabelPrint {
    private static let centerCrop: CGFloat = 0.78

    static func compute(_ image: UIImage) -> (d: String, a: String)? {
        // Normalise l'orientation (comme WineImageUtils.compressJPEG) avant de croper en
        // pixels bruts — sans ça, un CGImage tourné 90° donnerait un dHash incompatible avec
        // celui calculé côté web/Android sur la même étiquette.
        guard let normalized = normalizedUpright(image), let cg = normalized.cgImage else { return nil }
        guard let cropped = centerSquareCrop(cg) else { return nil }
        guard let d = dHash(cropped), let a = aHash(cropped) else { return nil }
        return (d, a)
    }

    private static func normalizedUpright(_ image: UIImage, maxDimension: CGFloat = 512) -> UIImage? {
        let maxSide = max(image.size.width, image.size.height)
        guard maxSide > 0 else { return nil }
        let scale = min(1, maxDimension / maxSide)
        let newSize = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        guard newSize.width >= 1, newSize.height >= 1 else { return nil }
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        let renderer = UIGraphicsImageRenderer(size: newSize, format: format)
        return renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: newSize))
        }
    }

    private static func centerSquareCrop(_ cg: CGImage) -> CGImage? {
        let w = CGFloat(cg.width)
        let h = CGFloat(cg.height)
        let side = min(w, h) * centerCrop
        guard side >= 1 else { return nil }
        let x = (w - side) / 2
        let y = (h - side) / 2
        return cg.cropping(to: CGRect(x: x, y: y, width: side, height: side))
    }

    private static func grayBuffer(_ cg: CGImage, width: Int, height: Int) -> [UInt8]? {
        guard width > 0, height > 0 else { return nil }
        let colorSpace = CGColorSpaceCreateDeviceGray()
        guard let ctx = CGContext(
            data: nil,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.none.rawValue
        ) else { return nil }
        ctx.interpolationQuality = .high
        ctx.draw(cg, in: CGRect(x: 0, y: 0, width: width, height: height))
        guard let data = ctx.data else { return nil }
        let ptr = data.bindMemory(to: UInt8.self, capacity: width * height)
        return Array(UnsafeBufferPointer(start: ptr, count: width * height))
    }

    private static func bitsToHex(_ bits: String) -> String {
        var hex = ""
        var i = bits.startIndex
        while i < bits.endIndex {
            let end = bits.index(i, offsetBy: 4, limitedBy: bits.endIndex) ?? bits.endIndex
            let nibble = String(bits[i..<end])
            hex += String(Int(nibble, radix: 2) ?? 0, radix: 16)
            i = end
        }
        return hex
    }

    private static func dHash(_ cg: CGImage) -> String? {
        let w = 9
        let h = 8
        guard let gray = grayBuffer(cg, width: w, height: h) else { return nil }
        var bits = ""
        for y in 0..<h {
            for x in 0..<(w - 1) {
                bits += gray[y * w + x] < gray[y * w + x + 1] ? "1" : "0"
            }
        }
        return bitsToHex(bits)
    }

    private static func aHash(_ cg: CGImage) -> String? {
        let size = 8
        guard let gray = grayBuffer(cg, width: size, height: size) else { return nil }
        let n = size * size
        let sum = gray.reduce(0) { $0 + Int($1) }
        let avg = Double(sum) / Double(n)
        var bits = ""
        for v in gray {
            bits += Double(v) >= avg ? "1" : "0"
        }
        return bitsToHex(bits)
    }
}
