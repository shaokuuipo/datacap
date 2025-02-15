import axios from 'axios'
import router from '@/router'
import { ResponseModel } from '@/model/response'
import { TokenUtils } from '@/utils/token'
import { AuthResponse } from '@/model/user/response/auth'
import { useToast } from '@/components/ui/toast/use-toast'

axios.defaults.headers.post['Access-Control-Allow-Origin'] = '*'
const { toast } = useToast()

export class HttpUtils
{
    private readonly configure

    constructor()
    {
        if (process.env.NODE_ENV === 'development') {
            axios.defaults.baseURL = 'http://localhost:9096'
        }
        else {
            axios.defaults.baseURL = window.location.protocol + '//' + window.location.hostname + (window.location.port ? ':' + window.location.port : '')
        }
        const auth: AuthResponse | undefined = TokenUtils.getAuthUser()
        if (auth) {
            this.configure = {
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': auth.type + ' ' + auth.token
                },
                cancelToken: undefined,
                params: undefined
            }
        }
    }

    handlerSuccessful(result: any): ResponseModel
    {
        const data = result.data
        let messageData = data.message
        if (data.message instanceof Array) {
            const messages: string[] = []
            data.message.forEach((element: { field: string; message: string }) => {
                messages.push(element.field + ': ' + element.message)
            })
            messageData = messages.join('\r\n')
        }
        const response: ResponseModel = {
            code: data.code,
            data: data.data,
            message: messageData,
            status: data.status
        }

        // If the authorization key does not match, clear the local token reload page
        if (response.code === 4003) {
            router.push(`/common/403?redirect=${ router.currentRoute.value.fullPath }`)
        }
        if (response.code === 5000) {
            this.handlerMessage(response.message)
        }
        return response
    }

    handlerFailed(error: any): ResponseModel
    {
        const response: ResponseModel = {
            code: 0,
            message: error.message,
            status: false
        }
        if (error.code === 'ERR_NETWORK') {
            router.push('/common/not_network?redirect=' + router.currentRoute.value.fullPath)
        }
        return response
    }

    get(url: string, params?: any): Promise<ResponseModel>
    {
        return new Promise((resolve) => {
            if (this.configure) {
                this.configure.params = params
            }
            axios.get(url, this.configure)
                 .then(result => {
                     resolve(this.handlerSuccessful(result))
                 }, error => {
                     this.handlerMessage(error.message)
                     resolve(this.handlerFailed(error))
                 })
        })
    }

    post(url: string, data = {}, cancelToken?: any): Promise<ResponseModel>
    {
        return new Promise((resolve) => {
            if (this.configure) {
                this.configure.cancelToken = cancelToken
            }
            axios.post(url, data, this.configure)
                 .then(result => {
                     resolve(this.handlerSuccessful(result))
                 }, error => {
                     this.handlerMessage(error.message)
                     resolve(this.handlerFailed(error))
                 })
        })
    }

    upload(url: string, data = {}, cancelToken?: any): Promise<ResponseModel>
    {
        return new Promise((resolve) => {
            if (this.configure) {
                this.configure.cancelToken = cancelToken
                this.configure.headers['Content-Type'] = 'multipart/form-data'
            }
            axios.post(url, data, this.configure)
                 .then(result => {
                     resolve(this.handlerSuccessful(result))
                 }, error => {
                     this.handlerMessage(error.message)
                     resolve(this.handlerFailed(error))
                 })
        })
    }

    put(url: string, data = {}): Promise<ResponseModel>
    {
        return new Promise((resolve) => {
            axios.put(url, data, this.configure)
                 .then(result => {
                     resolve(this.handlerSuccessful(result))
                 }, error => {
                     this.handlerMessage(error.message)
                     resolve(this.handlerFailed(error))
                 })
        })
    }

    delete(url: string): Promise<ResponseModel>
    {
        return new Promise((resolve) => {
            axios.delete(url, this.configure)
                 .then(result => {
                     resolve(this.handlerSuccessful(result))
                 }, error => {
                     this.handlerMessage(error.message)
                     resolve(this.handlerFailed(error))
                 })
        })
    }

    private handlerMessage(message: string)
    {
        toast({
            description: message,
            variant: 'destructive'
        })
    }

    getAxios()
    {
        return axios
    }
}
