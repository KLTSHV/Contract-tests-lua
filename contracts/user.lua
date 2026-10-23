-- Контракт потребителя для GET /users/42.
-- Меняйте только этот файл: пересборка Java не требуется.
local MIN_AGE = 18

function validate(user, response)
    if response.status ~= 200 then
        return false, "Ожидался HTTP 200, получен " .. response.status
    end
    local content_type = response.headers["content-type"] or ""
    if not string.find(string.lower(content_type), "application/json", 1, true) then
        return false, "Ожидался Content-Type application/json"
    end
    if type(user) ~= "table" then
        return false, "Тело ответа должно быть объектом пользователя"
    end
    if type(user.id) ~= "number" or user.id <= 0 or user.id % 1 ~= 0 then
        return false, "id должен быть положительным целым числом"
    end
    if type(user.name) ~= "string" or not string.find(user.name, "%S") then
        return false, "name должен быть непустой строкой"
    end
    if type(user.age) ~= "number" or user.age % 1 ~= 0 or user.age < MIN_AGE then
        return false, "age должен быть целым числом не меньше " .. MIN_AGE
    end
    return true, "Контракт пользователя выполнен"
end
