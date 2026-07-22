import XCTest
@testable import WineNative

final class ExampleModelTests: XCTestCase {
    func testPendingCheckinEquality() throws {
        let now = Date()
        let p1 = PendingCheckin(
            id: UUID(),
            createdAt: now,
            barcode: "123",
            wineName: "Test",
            producer: "B",
            style: "S",
            abv: "5",
            summary: "",
            rating: 4.0,
            flavors: [],
            hops: [],
            comment: "",
            vivinoBid: "",
            force: false,
            photoJPEGBase64: nil,
            location: nil
        )
        XCTAssertEqual(p1.wineName, "Test")
        XCTAssertEqual(p1.rating, 4.0)
        XCTAssertNil(p1.location)
    }
}
