# conf · 运行时文件配置

进程级配置走环境变量（.env 三环境：local/debug/release，godotenv 加载，见 main.go）。
本目录放不可进环境变量的文件配置（模板/词典/证书等），随镜像打包；敏感凭证仍走环境变量或密管。
