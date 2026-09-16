import SwiftUI

// 设计 token 唯一落点（contract/clients/ios.md 四-2）：
// Color(red:) / 字号字面量只允许出现在本文件，feature 内硬编码即缺陷（机检点）。
public extension Color {
    static let brandPrimary = Color(red: 0.18, green: 0.36, blue: 1.00)
    static let brandPrimaryDark = Color(red: 0.62, green: 0.69, blue: 1.00)
    static let brandSecondary = Color(red: 0.06, green: 0.71, blue: 0.65)
}

public enum DSRadius {
    public static let card: CGFloat = 12
    public static let button: CGFloat = 8
}
