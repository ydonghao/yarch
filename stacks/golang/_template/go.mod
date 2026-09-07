module {{.Module}}

go 1.24

require (
	github.com/cloudwego/hertz v0.10.2
	github.com/google/uuid v1.6.0
	github.com/joho/godotenv v1.5.1
	github.com/redis/go-redis/v9 v9.7.0
	github.com/ydonghao/yarch/stacks/golang {{.YarchVersion}}
	gorm.io/gorm v1.25.12
)
{{.ReplaceLine}}
